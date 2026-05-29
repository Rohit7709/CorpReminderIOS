package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application, private val repository: ReminderRepository) : AndroidViewModel(application) {

    // Dynamic Calendar & Live Weekday Mapping
    val daysOfWeek = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "Next Monday", "Next Tuesday", "Next Wednesday", "Next Thursday", "Next Friday", "Next Saturday", "Next Sunday"
    )
    
    val todayName: String = run {
        val cal = java.util.Calendar.getInstance()
        val dayFormat = java.text.SimpleDateFormat("EEEE", java.util.Locale.US)
        val name = dayFormat.format(cal.time)
        val matchedName = if (daysOfWeek.contains(name)) name else "Monday"
        matchedName
    }

    val dateMapping: Map<String, String> = run {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        // Align calendar to Monday of the current week
        val currentDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val daysToMonday = when (currentDayOfWeek) {
            java.util.Calendar.SUNDAY -> -6
            else -> 2 - currentDayOfWeek
        }
        cal.add(java.util.Calendar.DAY_OF_YEAR, daysToMonday)
        
        val map = mutableMapOf<String, String>()
        for (day in daysOfWeek) {
            map[day] = sdf.format(cal.time)
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        map
    }

    val holidays: Map<String, String> = run {
        try {
            val jsonString = getApplication<Application>().assets.open("holidays.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            val map = mutableMapOf<String, String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val date = obj.getString("date")
                map[date] = name
            }
            map
        } catch (e: Exception) {
            android.util.Log.e("ReminderViewModel", "Error loading holidays.json: ${e.message}", e)
            emptyMap()
        }
    }

    fun getHolidayNameForDate(dateStr: String): String? {
        return holidays[dateStr]
    }

    fun getHolidayNameForDay(day: String): String? {
        val dateStr = dateMapping[day] ?: return null
        return holidays[dateStr]
    }

    // App update check state flows based on SharedPreferences
    private val _requiredAppVersion = MutableStateFlow(1)
    val requiredAppVersion: StateFlow<Int> = _requiredAppVersion.asStateFlow()

    private val _localAppVersion = MutableStateFlow(1)
    val localAppVersion: StateFlow<Int> = _localAppVersion.asStateFlow()

    private val _updateDescription = MutableStateFlow("Please configure and run the updated application setup on your system.")
    val updateDescription: StateFlow<String> = _updateDescription.asStateFlow()

    private val _updateLink = MutableStateFlow("https://drive.google.com/drive/folders/1Im5CEoPEHDHXfnLi-6UdJyzp-U_p4B4y?usp=sharing")
    val updateLink: StateFlow<String> = _updateLink.asStateFlow()

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "required_app_version" -> {
                _requiredAppVersion.value = sharedPreferences.getInt("required_app_version", 1)
            }
            "local_app_version" -> {
                _localAppVersion.value = sharedPreferences.getInt("local_app_version", 1)
            }
            "update_description" -> {
                _updateDescription.value = sharedPreferences.getString("update_description", "Please configure and run the updated application setup on your system.") ?: ""
            }
            "update_link" -> {
                _updateLink.value = sharedPreferences.getString("update_link", "https://drive.google.com/drive/folders/1Im5CEoPEHDHXfnLi-6UdJyzp-U_p4B4y?usp=sharing") ?: ""
            }
        }
    }

    // Current State
    private val _selectedDay = MutableStateFlow(todayName)
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    // Database loading state
    private val _isDbLoading = MutableStateFlow(false)
    val isDbLoading: StateFlow<Boolean> = _isDbLoading.asStateFlow()

    private val _dbLoadingMessage = MutableStateFlow<String?>(null)
    val dbLoadingMessage: StateFlow<String?> = _dbLoadingMessage.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _lastActivityTime = MutableStateFlow(System.currentTimeMillis())
    val lastActivityTime: StateFlow<Long> = _lastActivityTime.asStateFlow()

    private val _sessionTimeoutDuration = MutableStateFlow(2 * 60 * 1000L) // 2 minutes (120000ms) default auto logout
    val sessionTimeoutDuration: StateFlow<Long> = _sessionTimeoutDuration.asStateFlow()

    private val _sessionTimeoutLoggedOut = MutableStateFlow(false)
    val sessionTimeoutLoggedOut: StateFlow<Boolean> = _sessionTimeoutLoggedOut.asStateFlow()

    fun clearSessionTimeoutLoggedOut() {
        _sessionTimeoutLoggedOut.value = false
    }

    val lastUserId: String
        get() = getApplication<Application>()
            .getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
            .getString("pref_saved_user_id", "") ?: ""

    fun resetSessionTimer() {
        _lastActivityTime.value = System.currentTimeMillis()
    }

    fun updateSessionTimeout(durationMs: Long) {
        _sessionTimeoutDuration.value = durationMs
        resetSessionTimer()
    }

    // Snooze state flow: Key is "userId_dateString_reminderId"
    private val _snoozes = MutableStateFlow<Map<String, TaskSnooze>>(emptyMap())
    val snoozes: StateFlow<Map<String, TaskSnooze>> = _snoozes.asStateFlow()

    fun isSelectedDayPast(): Boolean {
        val day = _selectedDay.value
        val todayDateStr = dateMapping[todayName] ?: ""
        val selectedDateStr = dateMapping[day] ?: ""
        if (todayDateStr.isNotEmpty() && selectedDateStr.isNotEmpty()) {
            return selectedDateStr < todayDateStr
        }
        val selectedIndex = daysOfWeek.indexOf(day).coerceAtLeast(0)
        val todayIndex = daysOfWeek.indexOf(todayName).coerceAtLeast(0)
        return selectedIndex < todayIndex
    }

    // Database flows
    val allUsers: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allReminders: StateFlow<List<Reminder>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCompletions: StateFlow<List<ReminderCompletion>> = repository.allCompletions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlannedLeaves: StateFlow<List<PlannedLeave>> = repository.allPlannedLeaves
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isReminderActiveOnDay(reminder: Reminder, day: String): Boolean {
        if (getHolidayNameForDay(day) != null) {
            return false
        }
        val cleanDay = if (day.startsWith("Next ")) day.substring(5) else day
        val currentIndex = daysOfWeek.indexOf(day).coerceAtLeast(0)

        // Check if reminder was created on a future simulated day/week relative to the currently active one
        val reminderStartDay = reminder.startDay
        if (reminderStartDay != null) {
            if (reminderStartDay.contains("-")) {
                val currentDayDate = dateMapping[day] ?: ""
                if (currentDayDate.isNotEmpty() && currentDayDate < reminderStartDay) {
                    return false
                }
            } else {
                val startIndex = daysOfWeek.indexOf(reminderStartDay)
                if (startIndex != -1 && currentIndex < startIndex) {
                    return false
                }
            }
        }

        // If it is one-time (not repetitive): it should ONLY match the exact target date or day
        if (!reminder.isRepetitive) {
            val currentDayDate = dateMapping[day] ?: ""
            return (reminder.startDay == currentDayDate) || (reminder.startDay == day) || (reminder.startDay != null && dateMapping[reminder.startDay] == currentDayDate)
        }

        // Check frequency match
        return when (reminder.frequency) {
            "DAILY" -> {
                // Daily corporate reminders apply Monday to Friday
                cleanDay != "Saturday" && cleanDay != "Sunday"
            }
            "FRIDAY" -> {
                val searchOrder = if (day.startsWith("Next ")) {
                    listOf("Next Friday", "Next Thursday", "Next Wednesday", "Next Tuesday", "Next Monday")
                } else {
                    listOf("Friday", "Thursday", "Wednesday", "Tuesday", "Monday")
                }
                val activeDay = searchOrder.firstOrNull { candidate ->
                    getHolidayNameForDay(candidate) == null
                }
                day == activeDay
            }
            "TUE_THU" -> {
                cleanDay == "Tuesday" || cleanDay == "Thursday"
            }
            "CUSTOM" -> {
                val targetDays = reminder.customDays?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
                targetDays.contains(cleanDay.lowercase())
            }
            else -> false
        }
    }

    // Active reminders for the selected simulated day of the week
    val activeRemindersForSelectedDay: StateFlow<List<Reminder>> = combine(_selectedDay, allReminders, _currentUser) { day, reminders, user ->
        reminders.filter { reminder ->
            // First check if user is targeted by this reminder
            val userMatches = reminder.targetUserId.isNullOrEmpty() || (user?.id != null && reminder.targetUserId.split(",").map { it.trim() }.contains(user.id))
            if (!userMatches) return@filter false

            // Do not show "Update Corporate Task App" in general list for non-admins (it will show as a popup instead)
            if (reminder.title == "Update Corporate Task App" && user?.role != "ADMIN") return@filter false

            isReminderActiveOnDay(reminder, day)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Check if there is an uncompleted "Update Corporate Task App" task for the user
    val pendingAppUpdateTask: StateFlow<Reminder?> = combine(
        _currentUser,
        _requiredAppVersion,
        _localAppVersion,
        _updateDescription,
        _updateLink
    ) { user, required, local, description, link ->
        // If the current user is an Admin, they don't see the update block (they manage / broadcast updates)
        if (user == null || user.role == "ADMIN") {
            null
        } else if (local < required) {
            // Return a synthetic Reminder
            Reminder(
                id = -999, // dummy ID indicating version check mode
                title = "Update Corporate Task App",
                description = "$description. Updated app can be found in this link : $link",
                frequency = "ONETIME",
                createdBy = "admin"
            )
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Map of reminder ID to completion for the currently logged in user on the active day
    val activeCompletionsMap: StateFlow<Map<Int, ReminderCompletion>> = combine(
        _currentUser,
        _selectedDay,
        allCompletions
    ) { user, day, completions ->
        val dateStr = dateMapping[day] ?: "2026-05-20"
        if (user == null) {
            emptyMap()
        } else {
            completions
                .filter { it.userId == user.id && it.dateString == dateStr }
                .associateBy { it.reminderId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private fun launchWithDbLoading(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _dbLoadingMessage.value = message
            _isDbLoading.value = true
            try {
                block()
            } catch (e: Exception) {
                android.util.Log.e("ReminderViewModel", "Database operation failed: ${e.message}", e)
            } finally {
                _isDbLoading.value = false
                _dbLoadingMessage.value = null
            }
        }
    }

    // Screen navigation model
    enum class Screen {
        EMPLOYEE_DASHBOARD,
        ADMIN_PORTAL,
        COMPLIANCE_MATRIX,
        TEAM_DIRECTORY
    }

    private val _currentScreen = MutableStateFlow(Screen.EMPLOYEE_DASHBOARD)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    init {
        // Read initial app version parameters from SharedPreferences
        val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
        _requiredAppVersion.value = prefs.getInt("required_app_version", 1)
        _localAppVersion.value = prefs.getInt("local_app_version", 1)
        _updateDescription.value = prefs.getString("update_description", "Please configure and run the updated application setup on your system.") ?: ""
        _updateLink.value = prefs.getString("update_link", "https://drive.google.com/drive/folders/1Im5CEoPEHDHXfnLi-6UdJyzp-U_p4B4y?usp=sharing") ?: ""
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Periodic session timeout activity monitor
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000) // inspect every second
                val user = _currentUser.value
                if (user != null) {
                    val idleTime = System.currentTimeMillis() - _lastActivityTime.value
                    if (idleTime >= _sessionTimeoutDuration.value) {
                        _sessionTimeoutLoggedOut.value = true
                        logout()
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.prepopulateIfNeeded()
            
            // Start real-time sync if database is configured
            restartRealtimeSync()
            
            launch {
                allUsers.collect { users ->
                    if (users.isNotEmpty()) {
                        // Keep the current user's role/properties updated if they change in the database
                        val currentId = _currentUser.value?.id
                        if (currentId != null) {
                            val updatedUser = users.firstOrNull { it.id == currentId }
                            if (updatedUser != null && (updatedUser.role != _currentUser.value?.role || updatedUser.name != _currentUser.value?.name)) {
                                _currentUser.value = updatedUser
                            }
                        }
                    }
                }
            }
        }
    }

    // Firebase Cloud Integration States & Actions
    private val _isFirebaseSyncing = MutableStateFlow(false)
    val isFirebaseSyncing: StateFlow<Boolean> = _isFirebaseSyncing.asStateFlow()

    private val _firebaseSyncStatus = MutableStateFlow<String?>(null)
    val firebaseSyncStatus: StateFlow<String?> = _firebaseSyncStatus.asStateFlow()

    fun backupToFirebase() {
        _isFirebaseSyncing.value = true
        _firebaseSyncStatus.value = "Backing up database to Firebase..."
        launchWithDbLoading("Backing up data to cloud...") {
            val result = com.example.util.FirebaseSyncHelper.backupToCloud(getApplication(), repository)
            if (result.isSuccess) {
                _firebaseSyncStatus.value = "Cloud Backup Succeeded!"
            } else {
                _firebaseSyncStatus.value = "Backup Failed: ${result.exceptionOrNull()?.message}"
            }
            _isFirebaseSyncing.value = false
        }
    }

    fun restoreFromFirebase() {
        _isFirebaseSyncing.value = true
        _firebaseSyncStatus.value = "Restoring database from Firebase..."
        launchWithDbLoading("Restoring data from cloud...") {
            val result = com.example.util.FirebaseSyncHelper.restoreFromCloud(getApplication(), repository)
            if (result.isSuccess) {
                _firebaseSyncStatus.value = "Cloud Restore Succeeded!"
            } else {
                _firebaseSyncStatus.value = "Restore Failed: ${result.exceptionOrNull()?.message}"
            }
            _isFirebaseSyncing.value = false
        }
    }

    fun clearSyncStatus() {
        _firebaseSyncStatus.value = null
    }

    fun autoSyncIfNeeded() {
        if (com.example.util.FirebaseSyncHelper.isAutoSyncEnabled(getApplication())) {
            viewModelScope.launch {
                val result = com.example.util.FirebaseSyncHelper.backupToCloud(getApplication(), repository)
                if (result.isFailure) {
                    android.util.Log.e("ReminderViewModel", "Auto-sync cloud backup failed: ${result.exceptionOrNull()?.message}")
                } else {
                    android.util.Log.d("ReminderViewModel", "Auto-sync cloud backup successfully updated!")
                }
            }
        }
    }

    fun restartRealtimeSync() {
        if (com.example.util.FirebaseSyncHelper.getDatabaseUrl(getApplication()).isNotEmpty()) {
            com.example.util.FirebaseSyncHelper.startRealtimeSync(getApplication(), repository)
        } else {
            com.example.util.FirebaseSyncHelper.stopRealtimeSync()
        }
    }

    override fun onCleared() {
        super.onCleared()
        val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        com.example.util.FirebaseSyncHelper.stopRealtimeSync()
    }

    // Actions
    fun addUser(name: String, userId: String, role: String): Boolean {
        if (name.isBlank() || userId.isBlank()) {
            return false
        }
        val cleanId = userId.trim().lowercase()
        val newUser = User(
            id = cleanId,
            name = name.trim(),
            role = role,
            password = "password"
        )
        launchWithDbLoading("Registering new user...") {
            repository.insertUser(newUser)
            autoSyncIfNeeded()
        }
        return true
    }

    fun deleteUser(user: User) {
        launchWithDbLoading("Deleting user...") {
            repository.deleteUser(user)
            autoSyncIfNeeded()
            if (_currentUser.value?.id == user.id) {
                logout()
            }
        }
    }

    fun updateUserRole(userId: String, newRole: String) {
        launchWithDbLoading("Updating user role...") {
            val existing = allUsers.value.firstOrNull { it.id == userId } ?: return@launchWithDbLoading
            val updated = existing.copy(role = newRole)
            repository.insertUser(updated)
            autoSyncIfNeeded()
            if (_currentUser.value?.id == userId) {
                _currentUser.value = updated
            }
        }
    }

    fun deleteUsers(userIds: List<String>) {
        launchWithDbLoading("Deleting multiple users...") {
            userIds.forEach { id ->
                val user = allUsers.value.firstOrNull { it.id == id }
                if (user != null) {
                    repository.deleteUser(user)
                    if (_currentUser.value?.id == user.id) {
                        logout()
                    }
                }
            }
            autoSyncIfNeeded()
        }
    }

    fun updateUsersRoles(userIds: List<String>, newRole: String) {
        launchWithDbLoading("Updating multiple user roles...") {
            userIds.forEach { id ->
                val user = allUsers.value.firstOrNull { it.id == id }
                if (user != null) {
                    val updated = user.copy(role = newRole)
                    repository.insertUser(updated)
                    if (_currentUser.value?.id == user.id) {
                        _currentUser.value = updated
                    }
                }
            }
            autoSyncIfNeeded()
        }
    }

    fun loginWithUserId(userId: String): String? {
        val query = userId.trim().lowercase()
        val matchingUser = allUsers.value.firstOrNull { it.id == query }
        
        if (matchingUser == null && query == "admin") {
            // Instant defensive fallback for default administrator on first boot
            val tempAdmin = User(
                id = "admin",
                name = "System Admin",
                role = "ADMIN"
            )
            _currentUser.value = tempAdmin
            resetSessionTimer()
            _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
            
            val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_logged_in_user_id", "admin")
                .putString("pref_saved_user_id", "admin")
                .apply()
            
            return null
        }

        if (matchingUser == null && query == "lead") {
            // Instant defensive fallback for default team lead on first boot
            val tempLead = User(
                id = "lead",
                name = "Sarah Connor (Lead)",
                role = "LEAD"
            )
            _currentUser.value = tempLead
            resetSessionTimer()
            _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
            
            val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_logged_in_user_id", "lead")
                .putString("pref_saved_user_id", "lead")
                .apply()
            
            return null
        }
        
        return if (matchingUser != null) {
            _currentUser.value = matchingUser
            resetSessionTimer()
            if (matchingUser.role != "ADMIN" && matchingUser.role != "LEAD") {
                _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
            }
            
            val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_logged_in_user_id", matchingUser.id)
                .putString("pref_saved_user_id", matchingUser.id)
                .apply()
            
            null
        } else {
            "User ID not found. Ask an Admin to create your account."
        }
    }

    fun getUserForLogin(userId: String): User? {
        val query = userId.trim().lowercase()
        return allUsers.value.firstOrNull { it.id.trim().lowercase() == query } ?: when (query) {
            "admin" -> User(id = "admin", name = "System Admin", role = "ADMIN", password = "password", passwordCreated = false)
            "lead" -> User(id = "lead", name = "Sarah Connor (Lead)", role = "LEAD", password = "password", passwordCreated = false)
            else -> null
        }
    }

    fun createPasswordForUser(
        user: User, 
        newPassword: String,
        q1: String? = null,
        a1: String? = null,
        q2: String? = null,
        a2: String? = null,
        q3: String? = null,
        a3: String? = null
    ) {
        val updatedUser = user.copy(
            password = newPassword,
            passwordCreated = true,
            question1 = q1,
            answer1 = a1,
            question2 = q2,
            answer2 = a2,
            question3 = q3,
            answer3 = a3
        )
        launchWithDbLoading("Creating user password...") {
            repository.insertUser(updatedUser)
            autoSyncIfNeeded()
            _currentUser.value = updatedUser
            resetSessionTimer()
            if (updatedUser.role != "ADMIN" && updatedUser.role != "LEAD") {
                _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
            }
            val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_logged_in_user_id", updatedUser.id)
                .putString("pref_saved_user_id", updatedUser.id)
                .apply()
        }
    }

    fun updateSecurityQuestions(
        user: User,
        q1: String?,
        a1: String?,
        q2: String?,
        a2: String?,
        q3: String?,
        a3: String?
    ) {
        val updated = user.copy(
            question1 = q1,
            answer1 = a1,
            question2 = q2,
            answer2 = a2,
            question3 = q3,
            answer3 = a3
        )
        launchWithDbLoading("Saving security questions...") {
            repository.insertUser(updated)
            autoSyncIfNeeded()
            if (_currentUser.value?.id == user.id) {
                _currentUser.value = updated
            }
        }
    }

    fun resetMPinForUser(userId: String, newPin: String) {
        val user = allUsers.value.firstOrNull { it.id.lowercase() == userId.trim().lowercase() }
        if (user != null) {
            val updated = user.copy(password = newPin, passwordCreated = true)
            launchWithDbLoading("Resetting MPin...") {
                repository.insertUser(updated)
                autoSyncIfNeeded()
            }
        }
    }

    fun verifyAndLogin(user: User, passwordInput: String): Boolean {
        if (user.password == passwordInput) {
            _currentUser.value = user
            resetSessionTimer()
            if (user.role != "ADMIN" && user.role != "LEAD") {
                _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
            }
            val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_logged_in_user_id", user.id)
                .putString("pref_saved_user_id", user.id)
                .apply()
            com.example.util.NotificationHelper.scheduleCheck(getApplication(), 0)
            return true
        }
        return false
    }

    fun logout() {
        _currentUser.value = null
        _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
        
        val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove("last_logged_in_user_id").apply()
    }

    // Actions
    fun setSimulatedDay(day: String) {
        if (daysOfWeek.contains(day)) {
            _selectedDay.value = day
        }
    }

    fun selectUser(user: User) {
        _currentUser.value = user
        resetSessionTimer()
        if (user.role != "ADMIN" && user.role != "LEAD") {
            _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
        } else if (user.role == "LEAD" && _currentScreen.value == Screen.TEAM_DIRECTORY) {
            _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
        }
        
        val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_logged_in_user_id", user.id)
            .putString("pref_saved_user_id", user.id)
            .apply()
        com.example.util.NotificationHelper.scheduleCheck(getApplication(), 0)
    }

    fun navigateTo(screen: Screen) {
        val user = _currentUser.value
        if (user?.role == "ADMIN") {
            _currentScreen.value = screen
        } else if (user?.role == "LEAD") {
            if (screen == Screen.TEAM_DIRECTORY) {
                _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
            } else {
                _currentScreen.value = screen
            }
        } else {
            if (screen != Screen.EMPLOYEE_DASHBOARD) {
                _currentScreen.value = Screen.EMPLOYEE_DASHBOARD
            } else {
                _currentScreen.value = screen
            }
        }
    }

    fun completeTask(reminderId: Int, payload: String? = null) {
        if (reminderId == -999) {
            val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
            val reqVersion = _requiredAppVersion.value
            prefs.edit().putInt("local_app_version", reqVersion).apply()
            _localAppVersion.value = reqVersion
            return
        }
        if (isSelectedDayPast()) return
        val user = _currentUser.value ?: return
        val day = _selectedDay.value
        val dateStr = dateMapping[day] ?: "2026-05-20"

        launchWithDbLoading("Completing task...") {
            repository.insertCompletion(
                ReminderCompletion(
                    reminderId = reminderId,
                    userId = user.id,
                    dateString = dateStr,
                    payload = payload,
                    isCompleted = true
                )
            )
            autoSyncIfNeeded()
        }
    }

    fun undoCompletion(reminderId: Int) {
        if (isSelectedDayPast()) return
        val user = _currentUser.value ?: return
        val day = _selectedDay.value
        val dateStr = dateMapping[day] ?: "2026-05-20"

        launchWithDbLoading("Undoing task completion...") {
            repository.removeCompletion(reminderId, user.id, dateStr)
            autoSyncIfNeeded()
        }
    }

    fun completeTaskOnBehalf(reminderId: Int, targetUserId: String, payload: String? = null) {
        val day = _selectedDay.value
        val dateStr = dateMapping[day] ?: "2026-05-20"

        launchWithDbLoading("Completing task on behalf...") {
            repository.insertCompletion(
                ReminderCompletion(
                    reminderId = reminderId,
                    userId = targetUserId,
                    dateString = dateStr,
                    payload = payload ?: "Completed by Admin/Lead",
                    isCompleted = true
                )
            )
            autoSyncIfNeeded()
        }
    }

    fun forceCompleteTask(reminderId: Int, dateStr: String) {
        if (reminderId == -999) {
            val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
            val reqVersion = _requiredAppVersion.value
            prefs.edit().putInt("local_app_version", reqVersion).apply()
            _localAppVersion.value = reqVersion
            return
        }
        val user = _currentUser.value ?: return
        launchWithDbLoading("Updating application setup...") {
            repository.insertCompletion(
                ReminderCompletion(
                    reminderId = reminderId,
                    userId = user.id,
                    dateString = dateStr,
                    payload = "App updated via popup dialog",
                    isCompleted = true
                )
            )
            autoSyncIfNeeded()
        }
    }

    fun removeCompletionOnBehalf(reminderId: Int, targetUserId: String) {
        val day = _selectedDay.value
        val dateStr = dateMapping[day] ?: "2026-05-20"

        launchWithDbLoading("Removing task completion...") {
            repository.removeCompletion(reminderId, targetUserId, dateStr)
            autoSyncIfNeeded()
        }
    }

    fun snoozeTask(reminderId: Int, durationMinutes: Int) {
        if (isSelectedDayPast()) return
        val user = _currentUser.value ?: return
        val day = _selectedDay.value
        val dateStr = dateMapping[day] ?: "2026-05-20"
        val key = "${user.id}_${dateStr}_$reminderId"

        val durationLabel = when (durationMinutes) {
            15 -> "15 minutes"
            30 -> "30 minutes"
            60 -> "1 hour"
            120 -> "2 hours"
            else -> "$durationMinutes mins"
        }

        val snoozeUntil = System.currentTimeMillis() + (durationMinutes * 60 * 1000)

        val newSnoozes = _snoozes.value.toMutableMap()
        newSnoozes[key] = TaskSnooze(
            reminderId = reminderId,
            userId = user.id,
            dateString = dateStr,
            snoozedUntilMillis = snoozeUntil,
            durationLabel = durationLabel
        )
        _snoozes.value = newSnoozes
    }

    fun unsnoozeTask(reminderId: Int) {
        if (isSelectedDayPast()) return
        val user = _currentUser.value ?: return
        val day = _selectedDay.value
        val dateStr = dateMapping[day] ?: "2026-05-20"
        val key = "${user.id}_${dateStr}_$reminderId"

        val newSnoozes = _snoozes.value.toMutableMap()
        newSnoozes.remove(key)
        _snoozes.value = newSnoozes
    }

    fun addNewReminder(title: String, description: String, frequency: String, customDays: List<String>?, targetUserId: String?, isRepetitive: Boolean, startDay: String?) {
        launchWithDbLoading("Creating compliance reminder...") {
            val customDaysStr = customDays?.joinToString(",")
            val reminder = Reminder(
                title = title,
                description = description,
                frequency = frequency,
                customDays = customDaysStr,
                targetUserId = targetUserId,
                createdBy = "admin",
                isSystemDefault = false,
                isRepetitive = isRepetitive,
                startDay = startDay
            )
            repository.insertReminder(reminder)
            autoSyncIfNeeded()
        }
    }

    fun broadcastAppUpdate(title: String, description: String, frequency: String, customDays: List<String>?, targetUserId: String?, isRepetitive: Boolean, startDay: String?) {
        launchWithDbLoading("Broadcasting update...") {
            val prefs = getApplication<Application>().getSharedPreferences("CorpRemind_Prefs", android.content.Context.MODE_PRIVATE)
            val nextVersion = _requiredAppVersion.value + 1
            
            val urlRegex = "https?://\\S+".toRegex()
            val match = urlRegex.find(description)
            val link = match?.value ?: "https://drive.google.com/drive/folders/1Im5CEoPEHDHXfnLi-6UdJyzp-U_p4B4y?usp=sharing"
            
            var cleanDesc = description.replace(urlRegex, "")
            cleanDesc = cleanDesc.replace("(?i)Updated app can be found in this link\\s*:\\s*".toRegex(), "")
            cleanDesc = cleanDesc.replace("(?i)Updated app can be found in this link".toRegex(), "")
            cleanDesc = cleanDesc.trim()
            if (cleanDesc.isEmpty()) {
                cleanDesc = "Please configure and run the updated application setup on your system."
            }

            prefs.edit()
                .putInt("required_app_version", nextVersion)
                .putString("update_description", cleanDesc)
                .putString("update_link", link)
                .apply()
            
            _requiredAppVersion.value = nextVersion
            _updateDescription.value = cleanDesc
            _updateLink.value = link
            
            autoSyncIfNeeded()
            com.example.util.NotificationHelper.scheduleCheck(getApplication(), 0)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        launchWithDbLoading("Deleting compliance reminder...") {
            repository.deleteReminder(reminder)
            autoSyncIfNeeded()
        }
    }

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    fun clearImportStatus() {
        _importStatus.value = null
    }

    fun importTeammatesFromUri(context: android.content.Context, uri: android.net.Uri) {
        _importStatus.value = "Importing teammates..."
        launchWithDbLoading("Importing teammates from file...") {
            try {
                val teammates = com.example.util.TeammateImporter.importFromFile(context, uri)
                if (teammates.isEmpty()) {
                    _importStatus.value = "Error: No teammates found. Make sure the file has columns labeling name and id."
                    return@launchWithDbLoading
                }

                var newlyAdded = 0
                var alreadyExisted = 0
                val existingUsers = allUsers.value

                teammates.forEach { (userId, name) ->
                    val cleanId = userId.trim().lowercase()
                    val exists = existingUsers.any { it.id == cleanId }
                    if (!exists) {
                        val newUser = User(
                            id = cleanId,
                            name = name.trim(),
                            role = "EMPLOYEE",
                            password = "password"
                        )
                        repository.insertUser(newUser)
                        newlyAdded++
                    } else {
                        alreadyExisted++
                    }
                }

                autoSyncIfNeeded()
                _importStatus.value = "Successfully imported $newlyAdded teammates (already existed: $alreadyExisted)!"
            } catch (e: Exception) {
                _importStatus.value = "Error during import: ${e.message}"
            }
        }
    }

    fun addPlannedLeave(startDate: String, endDate: String, reason: String): Boolean {
        val user = _currentUser.value ?: return false
        if (startDate.isBlank() || endDate.isBlank() || reason.isBlank()) {
            return false
        }
        val leave = PlannedLeave(
            userId = user.id,
            userName = user.name,
            startDate = startDate.trim(),
            endDate = endDate.trim(),
            reason = reason.trim(),
            status = "APPROVED"
        )
        launchWithDbLoading("Submitting planned leave...") {
            repository.insertPlannedLeave(leave)
            autoSyncIfNeeded()
        }
        return true
    }

    fun deletePlannedLeave(leave: PlannedLeave) {
        launchWithDbLoading("Removing planned leave...") {
            repository.deletePlannedLeave(leave)
            autoSyncIfNeeded()
        }
    }

    // Helper to calculate statistics for currently logged-in employee on the simulated day
    val todayStats: Flow<TaskStats> = combine(activeRemindersForSelectedDay, activeCompletionsMap) { reminders, completions ->
        val total = reminders.size
        val completed = reminders.count { completions.containsKey(it.id) }
        TaskStats(total = total, completed = completed)
    }

    data class TaskStats(val total: Int, val completed: Int) {
        val percentage: Float
            get() = if (total > 0) (completed.toFloat() / total.toFloat()) else 1.0f
    }

    data class TaskSnooze(
        val reminderId: Int,
        val userId: String,
        val dateString: String,
        val snoozedUntilMillis: Long,
        val durationLabel: String
    )

    // Factory Class for construction
    class Factory(
        private val application: Application,
        private val repository: ReminderRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReminderViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ReminderViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
