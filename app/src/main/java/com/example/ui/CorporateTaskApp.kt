package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Reminder
import com.example.data.ReminderCompletion
import com.example.data.User
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun getDisplayDayName(dayName: String, todayName: String, daysOfWeek: List<String>): String {
    if (dayName.startsWith("Next ")) {
        val baseDay = dayName.substring(5)
        val todayIndex = daysOfWeek.indexOf(todayName).coerceAtLeast(0)
        val baseIndex = daysOfWeek.indexOf(baseDay).coerceAtLeast(0)
        if (baseIndex < todayIndex) {
            return "This $baseDay"
        }
    }
    return dayName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorporateTaskApp(viewModel: ReminderViewModel, modifier: Modifier = Modifier) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val allReminders by viewModel.allReminders.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allCompletions by viewModel.allCompletions.collectAsStateWithLifecycle()

    val activeReminders by viewModel.activeRemindersForSelectedDay.collectAsStateWithLifecycle()
    val activeCompletions by viewModel.activeCompletionsMap.collectAsStateWithLifecycle()
    val pendingAppUpdateTask by viewModel.pendingAppUpdateTask.collectAsStateWithLifecycle()
    var activeWfoDialog by remember { mutableStateOf<Reminder?>(null) }

    val todayIndex = viewModel.daysOfWeek.indexOf(viewModel.todayName).coerceAtLeast(0)
    val selectedIndex = viewModel.daysOfWeek.indexOf(selectedDay).coerceAtLeast(0)
    val isPastDay = selectedIndex < todayIndex
    val isFutureDay = selectedIndex > todayIndex
    val isEmployee = currentUser?.role != "ADMIN" && currentUser?.role != "LEAD"
    val holidayName = viewModel.getHolidayNameForDay(selectedDay)
    val isHoliday = holidayName != null
    val isPastDisabled = isPastDay || isFutureDay || isHoliday

    var currentWallTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            currentWallTime = System.currentTimeMillis()
        }
    }

    var showSecurityQuestionsSetup by remember { mutableStateOf(false) }
    var q1Selection by remember { mutableStateOf("") }
    var a1Input by remember { mutableStateOf("") }
    var q2Selection by remember { mutableStateOf("") }
    var a2Input by remember { mutableStateOf("") }
    var q3Selection by remember { mutableStateOf("") }
    var a3Input by remember { mutableStateOf("") }
    var activePickingQuestionSlot by remember { mutableStateOf<Int?>(null) }
    var questionsErrorMsg by remember { mutableStateOf<String?>(null) }

    val isDbLoading by viewModel.isDbLoading.collectAsStateWithLifecycle()
    val dbLoadingMessage by viewModel.dbLoadingMessage.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        viewModel.resetSessionTimer()
                    }
                }
            }
    ) {
        if (currentUser == null) {
            LoginRegisterScreen(
                viewModel = viewModel,
                modifier = modifier
            )
        } else {
            Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                // Main Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CorporateFare,
                            contentDescription = "CorpRemind Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "CorpRemind",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Notification Bell and Persona Selector Actions Row
                    var showUserSelector by remember { mutableStateOf(false) }
                    var showNotificationsDialog by remember { mutableStateOf(false) }
                    var showGlobalAppUpdateDialog by remember { mutableStateOf(false) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Global Administrator broadcast icon
                        if (currentUser?.role == "ADMIN") {
                            IconButton(
                                onClick = { showGlobalAppUpdateDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .testTag("admin_global_broadcast_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = "Trigger App Update Notification",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        // Polished Notification Bell
                        val uncompletedCount = activeReminders.filter { activeCompletions[it.id] == null }.size

                        Box(
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(
                                onClick = { showNotificationsDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (uncompletedCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .testTag("notification_bell_button")
                            ) {
                                Icon(
                                    imageVector = if (uncompletedCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = if (uncompletedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (uncompletedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(MaterialTheme.colorScheme.error, shape = CircleShape)
                                        .size(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = uncompletedCount.toString(),
                                        color = MaterialTheme.colorScheme.onError,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        // Persona Selector (Just an icon button with person icon, clicking shows profile details and logout button)
                        IconButton(
                            onClick = { showUserSelector = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                )
                                .testTag("persona_selector")
                        ) {
                            Icon(
                                imageVector = if (currentUser?.role == "ADMIN") Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                contentDescription = "View Profile",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // User Persona Dialog
                    if (showUserSelector) {
                        val activeProfile = currentUser
                        Dialog(onDismissRequest = { showUserSelector = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    IconButton(
                                        onClick = { showUserSelector = false },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.padding(top = 28.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Corporate Profile Details",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .background(
                                                    color = if (activeProfile?.role == "ADMIN") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (activeProfile?.role == "ADMIN") Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if (activeProfile?.role == "ADMIN") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        Text(
                                            text = activeProfile?.name ?: "Unknown User",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        Text(
                                            text = "Functional Role: ${activeProfile?.role ?: "EMPLOYEE"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                         OutlinedButton(
                                             onClick = {
                                                 q1Selection = activeProfile?.question1 ?: ""
                                                 a1Input = activeProfile?.answer1 ?: ""
                                                 q2Selection = activeProfile?.question2 ?: ""
                                                 a2Input = activeProfile?.answer2 ?: ""
                                                 q3Selection = activeProfile?.question3 ?: ""
                                                 a3Input = activeProfile?.answer3 ?: ""
                                                 questionsErrorMsg = null
                                                 showSecurityQuestionsSetup = true
                                             },
                                             shape = RoundedCornerShape(12.dp),
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .height(48.dp)
                                                 .testTag("setup_questions_profile_btn")
                                         ) {
                                             Icon(
                                                 imageVector = Icons.Default.Security,
                                                 contentDescription = "Security",
                                                 modifier = Modifier.size(18.dp)
                                             )
                                             Spacer(modifier = Modifier.width(8.dp))
                                             Text(
                                                 text = "Setup Security Questions",
                                                 fontWeight = FontWeight.SemiBold
                                             )
                                          }

                                         if (activeProfile?.id != null) {
                                            Text(
                                                text = "Employee ID: ${activeProfile.id}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(24.dp))
                                        
                                        Button(
                                            onClick = {
                                                viewModel.logout()
                                                showUserSelector = false
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("logout_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = "Log Out",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Log Out",
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                     // Security Questions Setup and Pick Dialogs
                     if (showSecurityQuestionsSetup) {
                         AlertDialog(
                             onDismissRequest = { showSecurityQuestionsSetup = false },
                             title = {
                                 Text(
                                     text = "Configure Security Questions",
                                     style = MaterialTheme.typography.titleMedium,
                                     fontWeight = FontWeight.Bold
                                 )
                             },
                             text = {
                                 Column(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .verticalScroll(rememberScrollState()),
                                     verticalArrangement = Arrangement.spacedBy(12.dp)
                                 ) {
                                     Text(
                                         text = "Choose and answer three security questions to protect your corporate console and enable independent MPin recovery.",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant
                                     )

                                     if (questionsErrorMsg != null) {
                                         Surface(
                                             color = MaterialTheme.colorScheme.errorContainer,
                                             contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                             shape = RoundedCornerShape(8.dp),
                                             modifier = Modifier.fillMaxWidth()
                                         ) {
                                             Text(
                                                 text = questionsErrorMsg ?: "",
                                                 style = MaterialTheme.typography.labelSmall,
                                                 modifier = Modifier.padding(10.dp)
                                             )
                                         }
                                     }

                                     // Question 1 Slot
                                     Card(
                                         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                         shape = RoundedCornerShape(12.dp)
                                     ) {
                                         Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                             Text("Question 1", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                             OutlinedButton(
                                                 onClick = { activePickingQuestionSlot = 1 },
                                                 modifier = Modifier.fillMaxWidth().testTag("profile_q1_btn"),
                                                 shape = RoundedCornerShape(8.dp)
                                             ) {
                                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                                     Text(
                                                         text = q1Selection.ifEmpty { "Select Question 1..." },
                                                         style = MaterialTheme.typography.bodyMedium,
                                                         modifier = Modifier.weight(1f),
                                                         textAlign = TextAlign.Start
                                                     )
                                                     Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                 }
                                             }
                                             OutlinedTextField(
                                                 value = a1Input,
                                                 onValueChange = { a1Input = it; questionsErrorMsg = null },
                                                 label = { Text("Secret Answer 1") },
                                                 singleLine = true,
                                                 shape = RoundedCornerShape(8.dp),
                                                 modifier = Modifier.fillMaxWidth().testTag("profile_a1_field")
                                             )
                                         }
                                     }

                                     // Question 2 Slot
                                     Card(
                                         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                         shape = RoundedCornerShape(12.dp)
                                     ) {
                                         Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                             Text("Question 2", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                             OutlinedButton(
                                                 onClick = { activePickingQuestionSlot = 2 },
                                                 modifier = Modifier.fillMaxWidth().testTag("profile_q2_btn"),
                                                 shape = RoundedCornerShape(8.dp)
                                             ) {
                                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                                     Text(
                                                         text = q2Selection.ifEmpty { "Select Question 2..." },
                                                         style = MaterialTheme.typography.bodyMedium,
                                                         modifier = Modifier.weight(1f),
                                                         textAlign = TextAlign.Start
                                                     )
                                                     Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                 }
                                             }
                                             OutlinedTextField(
                                                 value = a2Input,
                                                 onValueChange = { a2Input = it; questionsErrorMsg = null },
                                                 label = { Text("Secret Answer 2") },
                                                 singleLine = true,
                                                 shape = RoundedCornerShape(8.dp),
                                                 modifier = Modifier.fillMaxWidth().testTag("profile_a2_field")
                                             )
                                         }
                                     }

                                     // Question 3 Slot
                                     Card(
                                         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                         shape = RoundedCornerShape(12.dp)
                                     ) {
                                         Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                             Text("Question 3", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                             OutlinedButton(
                                                 onClick = { activePickingQuestionSlot = 3 },
                                                 modifier = Modifier.fillMaxWidth().testTag("profile_q3_btn"),
                                                 shape = RoundedCornerShape(8.dp)
                                             ) {
                                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                                     Text(
                                                         text = q3Selection.ifEmpty { "Select Question 3..." },
                                                         style = MaterialTheme.typography.bodyMedium,
                                                         modifier = Modifier.weight(1f),
                                                         textAlign = TextAlign.Start
                                                     )
                                                     Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                 }
                                             }
                                             OutlinedTextField(
                                                 value = a3Input,
                                                 onValueChange = { a3Input = it; questionsErrorMsg = null },
                                                 label = { Text("Secret Answer 3") },
                                                 singleLine = true,
                                                 shape = RoundedCornerShape(8.dp),
                                                 modifier = Modifier.fillMaxWidth().testTag("profile_a3_field")
                                             )
                                         }
                                     }
                                 }
                             },
                             confirmButton = {
                                 Button(
                                     onClick = {
                                         if (q1Selection.isBlank() || q2Selection.isBlank() || q3Selection.isBlank()) {
                                             questionsErrorMsg = "Please configure all 3 security questions."
                                         } else if (a1Input.trim().isBlank() || a2Input.trim().isBlank() || a3Input.trim().isBlank()) {
                                             questionsErrorMsg = "Please supply secret answers for all questions."
                                         } else if (q1Selection == q2Selection || q2Selection == q3Selection || q1Selection == q3Selection) {
                                             questionsErrorMsg = "All selected questions must be distinct."
                                         } else {
                                             val u = currentUser
                                             if (u != null) {
                                                 viewModel.updateSecurityQuestions(
                                                     u,
                                                     q1Selection, a1Input.trim(),
                                                     q2Selection, a2Input.trim(),
                                                     q3Selection, a3Input.trim()
                                                 )
                                                 showSecurityQuestionsSetup = false
                                             }
                                         }
                                     },
                                     modifier = Modifier.testTag("profile_save_questions_btn")
                                 ) {
                                     Text("Save")
                                 }
                             },
                             dismissButton = {
                                 OutlinedButton(onClick = { showSecurityQuestionsSetup = false }) {
                                     Text("Cancel")
                                 }
                             }
                         )
                     }

                     if (activePickingQuestionSlot != null) {
                         AlertDialog(
                             onDismissRequest = { activePickingQuestionSlot = null },
                             title = {
                                 Text(
                                     text = "Select Security Question",
                                     style = MaterialTheme.typography.titleMedium,
                                     fontWeight = FontWeight.Bold
                                 )
                             },
                             text = {
                                 Column(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .verticalScroll(rememberScrollState()),
                                     verticalArrangement = Arrangement.spacedBy(8.dp)
                                 ) {
                                     PresetSecurityQuestions.forEach { question ->
                                         val isAlreadyChosen = when (activePickingQuestionSlot) {
                                             1 -> question == q2Selection || question == q3Selection
                                             2 -> question == q1Selection || question == q3Selection
                                             3 -> question == q1Selection || question == q2Selection
                                             else -> false
                                         }
                                         Card(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .clickable(enabled = !isAlreadyChosen) {
                                                     when (activePickingQuestionSlot) {
                                                         1 -> q1Selection = question
                                                         2 -> q2Selection = question
                                                         3 -> q3Selection = question
                                                     }
                                                     activePickingQuestionSlot = null
                                                 },
                                             colors = CardDefaults.cardColors(
                                                 containerColor = if (isAlreadyChosen) {
                                                     MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                 } else {
                                                     MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                                 }
                                             ),
                                             shape = RoundedCornerShape(12.dp)
                                         ) {
                                             Row(
                                                 modifier = Modifier.padding(14.dp),
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 Text(
                                                     text = question,
                                                     style = MaterialTheme.typography.bodyMedium,
                                                     modifier = Modifier.weight(1f),
                                                     color = if (isAlreadyChosen) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                 )
                                                 if (isAlreadyChosen) {
                                                     Text("Chosen", style = MaterialTheme.typography.labelSmall)
                                                 }
                                             }
                                         }
                                     }
                                 }
                             },
                             confirmButton = {
                                 TextButton(onClick = { activePickingQuestionSlot = null }) {
                                     Text("Cancel")
                                 }
                             }
                         )
                     }

                      if (showGlobalAppUpdateDialog) {
                          val todayStr = viewModel.dateMapping[selectedDay] ?: viewModel.todayName
                          AlertDialog(
                              onDismissRequest = { showGlobalAppUpdateDialog = false },
                              title = {
                                  Row(verticalAlignment = Alignment.CenterVertically) {
                                      Icon(
                                          imageVector = Icons.Default.Campaign,
                                          contentDescription = null,
                                          tint = MaterialTheme.colorScheme.primary,
                                          modifier = Modifier.size(28.dp)
                                      )
                                      Spacer(modifier = Modifier.width(8.dp))
                                      Text(
                                          text = "Broadcast App Update",
                                          style = MaterialTheme.typography.titleLarge,
                                          fontWeight = FontWeight.Bold
                                      )
                                  }
                              },
                              text = {
                                  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                      Text(
                                          text = "This will trigger an urgent notification and assign a one-time task to all employees (including leads) for the current day to update their application setup.",
                                          style = MaterialTheme.typography.bodyMedium
                                      )
                                      
                                      Card(
                                          colors = CardDefaults.cardColors(
                                              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                          ),
                                          shape = RoundedCornerShape(12.dp),
                                          modifier = Modifier.fillMaxWidth()
                                      ) {
                                          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                              Text(
                                                  text = "TASK PREVIEW",
                                                  style = MaterialTheme.typography.labelSmall,
                                                  fontWeight = FontWeight.Bold,
                                                  color = MaterialTheme.colorScheme.primary
                                              )
                                              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                              Text(
                                                  text = "Title: Update Corporate Task App",
                                                  style = MaterialTheme.typography.bodySmall,
                                                  fontWeight = FontWeight.SemiBold
                                              )
                                              Text(
                                                  text = "Details: Please configure and run the updated application setup on your system.",
                                                  style = MaterialTheme.typography.bodySmall,
                                                  color = MaterialTheme.colorScheme.onSurfaceVariant
                                              )
                                              Text(
                                                  text = "Target: All Employees & Leads",
                                                  style = MaterialTheme.typography.bodySmall,
                                                  color = MaterialTheme.colorScheme.onSurfaceVariant
                                              )
                                              Text(
                                                  text = "Simulated Date: $todayStr",
                                                  style = MaterialTheme.typography.bodySmall,
                                                  color = MaterialTheme.colorScheme.onSurfaceVariant
                                              )
                                          }
                                      }
                                  }
                              },
                              confirmButton = {
                                  Button(
                                      onClick = {
                                          val finalStartDay = viewModel.dateMapping[selectedDay] ?: selectedDay
                                          viewModel.broadcastAppUpdate(
                                              title = "Update Corporate Task App",
                                              description = "Please configure and run the updated application setup on your system. Updated app can be found in this link : https://drive.google.com/drive/folders/1Im5CEoPEHDHXfnLi-6UdJyzp-U_p4B4y?usp=sharing",
                                              frequency = "ONETIME",
                                              customDays = null,
                                              targetUserId = null,
                                              isRepetitive = false,
                                              startDay = finalStartDay
                                          )
                                          showGlobalAppUpdateDialog = false
                                      },
                                      modifier = Modifier.testTag("confirm_global_broadcast_update_btn")
                                  ) {
                                      Text("Confirm & Send")
                                  }
                              },
                              dismissButton = {
                                  TextButton(
                                      onClick = { showGlobalAppUpdateDialog = false }
                                  ) {
                                      Text("Cancel")
                                  }
                              }
                          )
                      }

                     // Simulated System Notification Shade
                    if (showNotificationsDialog) {
                        Dialog(onDismissRequest = { showNotificationsDialog = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 8.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(20.dp)
                                        .fillMaxWidth()
                                ) {
                                    // Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text(
                                                text = "Corporate Inbox & Alerts",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(onClick = { showNotificationsDialog = false }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Awaiting compliance actions for $selectedDay",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    val todayReminders = activeReminders
                                    if (todayReminders.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.DoneAll,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2E7D32),
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "Zero Pending Alerts",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "You have no outstanding duties on this simulation day.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 350.dp)
                                        ) {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(todayReminders) { reminder ->
                                                    val isCompleted = activeCompletions[reminder.id] != null
                                                    val isCurrentlySnoozed = false
                                                    val isDark = isSystemInDarkTheme()
                                                    val cardBg = when {
                                                        isCompleted -> if (isDark) Color(0xFF112D19) else Color(0xFFF1F8E9)
                                                        isCurrentlySnoozed -> if (isDark) Color(0xFF2D1F10) else Color(0xFFFFF8E1)
                                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    }
                                                    val cardBorderColor = when {
                                                        isCompleted -> if (isDark) Color(0xFF2E7D32) else Color(0xFFC8E6C9)
                                                        isCurrentlySnoozed -> if (isDark) Color(0xFFE65100) else Color(0xFFFFECB3)
                                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                    }
                                                    val iconBgColor = when {
                                                        isCompleted -> if (isDark) Color(0xFF1B3B22) else Color(0xFFC8E6C9)
                                                        isCurrentlySnoozed -> if (isDark) Color(0xFF3E2723) else Color(0xFFFFECB3)
                                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                                    }
                                                     val iconTint = when {
                                                         isCompleted -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                                         isCurrentlySnoozed -> if (isDark) Color(0xFFFFB74D) else Color(0xFFF57F17)
                                                         else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                     }
                                                     val titleColor = when {
                                                         isCompleted -> if (isDark) Color(0xFFC8E6C9) else Color(0xFF1B5E20)
                                                         isCurrentlySnoozed -> if (isDark) Color(0xFFFFCC80) else Color(0xFF5D4037)
                                                         else -> MaterialTheme.colorScheme.onSurface
                                                     }
                                                     val descriptionColor = when {
                                                         isCompleted -> if (isDark) Color(0xFFA5D6A7) else Color(0xFF43A047)
                                                         isCurrentlySnoozed -> if (isDark) Color(0xFFFFB74D) else Color(0xFF8D6E63)
                                                         else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                     }
                                                     val statusTextColor = when {
                                                         isCompleted -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                                         else -> if (isDark) Color(0xFFFFD54F) else Color(0xFFE65100)
                                                     }


                                                    var showSnoozeOptions by remember(reminder.id) { mutableStateOf(false) }
                                                    var customDurationStr by remember { mutableStateOf("") }
                                                    val notificationIcon = when {
                                                        reminder.title.contains("Timesheet", ignoreCase = true) -> Icons.Default.Timer
                                                        reminder.title.contains("Exception", ignoreCase = true) -> Icons.Default.Warning
                                                        reminder.title.contains("WFO", ignoreCase = true) || reminder.description.contains("WFO Status", ignoreCase = true) -> Icons.Default.LocationOn
                                                        else -> Icons.Default.TaskAlt
                                                    }

                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("notification_card_${reminder.id}"),
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = cardBg
                                                        ),
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            width = 1.dp,
                                                            color = cardBorderColor
                                                        )
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(36.dp)
                                                                        .background(
                                                                            color = iconBgColor,
                                                                            shape = RoundedCornerShape(8.dp)
                                                                        ),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (isCurrentlySnoozed) Icons.Default.AccessTime else notificationIcon,
                                                                        contentDescription = null,
                                                                        tint = iconTint,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = reminder.title,
                                                                        style = MaterialTheme.typography.titleSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = titleColor
                                                                    )
                                                                    Text(
                                                                        text = reminder.description,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        color = descriptionColor
                                                                    )
                                                                }
                                                            }

                                                            Spacer(modifier = Modifier.height(8.dp))

                                                            if (isCompleted) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.End,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    TextButton(
                                                                        onClick = { viewModel.undoCompletion(reminder.id) },
                                                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                                        enabled = !isPastDisabled,
                                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Close,
                                                                            contentDescription = null,
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text(
                                                                            text = "Mark Not Completed",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            fontWeight = FontWeight.Bold,
                                                                            maxLines = 1
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Button(
                                                                        onClick = {
                                                                            if ((reminder.title.contains("WFO", ignoreCase = true) || reminder.title.contains("Attendance", ignoreCase = true) || reminder.description.contains("WFO Status", ignoreCase = true)) && !reminder.title.contains("Exception", ignoreCase = true)) {
                                                                                activeWfoDialog = reminder
                                                                                showNotificationsDialog = false
                                                                            } else {
                                                                                viewModel.completeTask(reminder.id, "Completed via Notification")
                                                                            }
                                                                        },
                                                                        enabled = !isPastDisabled,
                                                                        shape = RoundedCornerShape(12.dp),
                                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                                        modifier = Modifier.height(32.dp).testTag("notif_complete_trigger_${reminder.id}")
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = if ((reminder.title.contains("WFO", ignoreCase = true) || reminder.title.contains("Attendance", ignoreCase = true)) && !reminder.title.contains("Exception", ignoreCase = true)) Icons.Default.LocationOn else Icons.Default.Check,
                                                                            contentDescription = null,
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text(
                                                                            text = if ((reminder.title.contains("WFO", ignoreCase = true) || reminder.title.contains("Attendance", ignoreCase = true)) && !reminder.title.contains("Exception", ignoreCase = true)) "Set Status" else "Mark Completed",
                                                                            style = MaterialTheme.typography.bodySmall
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = { showNotificationsDialog = false },
                                        modifier = Modifier.align(Alignment.End),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }
                }

                // Weekday Review Block (Horizontal tab to select day of week for review)
                val showWeekdayView = currentScreen == ReminderViewModel.Screen.EMPLOYEE_DASHBOARD ||
                        currentScreen == ReminderViewModel.Screen.COMPLIANCE_MATRIX

                if (showWeekdayView) {
                    Text(
                        text = "📅 WEEKDAY VIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )

                    ScrollableTabRow(
                        selectedTabIndex = viewModel.daysOfWeek.indexOf(selectedDay).coerceAtLeast(0),
                        edgePadding = 16.dp,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[viewModel.daysOfWeek.indexOf(selectedDay).coerceAtLeast(0)]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        containerColor = Color.Transparent
                    ) {
                    viewModel.daysOfWeek.forEach { dayName ->
                        Tab(
                            selected = selectedDay == dayName,
                            onClick = { viewModel.setSimulatedDay(dayName) },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val cleanDay = if (dayName.startsWith("Next ")) dayName.substring(5) else dayName
                                    val isWorkday = cleanDay != "Saturday" && cleanDay != "Sunday"
                                    Text(
                                        text = getDisplayDayName(dayName, viewModel.todayName, viewModel.daysOfWeek),
                                        fontWeight = if (selectedDay == dayName) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedDay == dayName) {
                                            MaterialTheme.colorScheme.primary
                                        } else if (isWorkday) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        }
                                    )
                                    val dateStr = viewModel.dateMapping[dayName] ?: ""
                                    val formattedDate = try {
                                        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                        val formatter = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                                        parser.parse(dateStr)?.let { formatter.format(it) } ?: dateStr
                                    } catch (e: java.lang.Exception) {
                                        dateStr
                                    }
                                    Text(
                                        text = formattedDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = if (selectedDay == dayName) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selectedDay == dayName) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        },
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )

                                }
                            }
                        )
                    }
                }
                }
            }
        },
        bottomBar = {
            if (currentUser?.role == "ADMIN" || currentUser?.role == "LEAD") {
                val isDark = isSystemInDarkTheme()
                val navBarContainerColor = if (isDark) Color(0xFF1D1F2B) else Color(0xFFF9F9FB)
                val navBarIndicatorColor = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer
                val activeIconColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                val activeTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                val inactiveIconColor = if (isDark) Color(0xFF8E919F) else Color(0xFF74777F)
                val inactiveTextColor = if (isDark) Color(0xFF8E919F) else Color(0xFF74777F)

                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = navBarContainerColor
                ) {
                    val isDeskSelected = currentScreen == ReminderViewModel.Screen.EMPLOYEE_DASHBOARD
                    NavigationBarItem(
                        selected = isDeskSelected,
                        onClick = { viewModel.navigateTo(ReminderViewModel.Screen.EMPLOYEE_DASHBOARD) },
                        icon = { 
                            Icon(
                                imageVector = Icons.Default.Dashboard, 
                                contentDescription = "Dashboard",
                                modifier = Modifier.size(if (isDeskSelected) 26.dp else 22.dp)
                            ) 
                        },
                        label = {
                            Text(
                                text = "My Desk",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isDeskSelected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = activeIconColor,
                            selectedTextColor = activeTextColor,
                            indicatorColor = navBarIndicatorColor,
                            unselectedIconColor = inactiveIconColor,
                            unselectedTextColor = inactiveTextColor
                        ),
                        modifier = Modifier.testTag("nav_my_desk")
                    )

                    if (currentUser?.role == "ADMIN" || currentUser?.role == "LEAD") {
                        val isConfigSelected = currentScreen == ReminderViewModel.Screen.ADMIN_PORTAL
                        NavigationBarItem(
                            selected = isConfigSelected,
                            onClick = { viewModel.navigateTo(ReminderViewModel.Screen.ADMIN_PORTAL) },
                            icon = { 
                                Icon(
                                    imageVector = Icons.Default.AddCircle, 
                                    contentDescription = "Admin Portal",
                                    modifier = Modifier.size(if (isConfigSelected) 26.dp else 22.dp)
                                ) 
                            },
                            label = {
                                Text(
                                    text = "Configure",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isConfigSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    ),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = activeIconColor,
                                selectedTextColor = activeTextColor,
                                indicatorColor = navBarIndicatorColor,
                                unselectedIconColor = inactiveIconColor,
                                unselectedTextColor = inactiveTextColor
                            ),
                            modifier = Modifier.testTag("nav_admin_portal")
                        )
                    }

                    if (currentUser?.role == "ADMIN") {
                        val isDirSelected = currentScreen == ReminderViewModel.Screen.TEAM_DIRECTORY
                        NavigationBarItem(
                            selected = isDirSelected,
                            onClick = { viewModel.navigateTo(ReminderViewModel.Screen.TEAM_DIRECTORY) },
                            icon = { 
                                Icon(
                                    imageVector = Icons.Default.People, 
                                    contentDescription = "Team Directory",
                                    modifier = Modifier.size(if (isDirSelected) 26.dp else 22.dp)
                                ) 
                            },
                            label = {
                                Text(
                                    text = "Directory",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isDirSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    ),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = activeIconColor,
                                selectedTextColor = activeTextColor,
                                indicatorColor = navBarIndicatorColor,
                                unselectedIconColor = inactiveIconColor,
                                unselectedTextColor = inactiveTextColor
                            ),
                            modifier = Modifier.testTag("nav_team_directory")
                        )
                    }

                    val isMetricsSelected = currentScreen == ReminderViewModel.Screen.COMPLIANCE_MATRIX
                    NavigationBarItem(
                        selected = isMetricsSelected,
                        onClick = { viewModel.navigateTo(ReminderViewModel.Screen.COMPLIANCE_MATRIX) },
                        icon = { 
                            Icon(
                                imageVector = Icons.Default.Group, 
                                contentDescription = "Compliance",
                                modifier = Modifier.size(if (isMetricsSelected) 26.dp else 22.dp)
                            ) 
                        },
                        label = {
                            Text(
                                text = "Metrics",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isMetricsSelected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = activeIconColor,
                            selectedTextColor = activeTextColor,
                            indicatorColor = navBarIndicatorColor,
                            unselectedIconColor = inactiveIconColor,
                            unselectedTextColor = inactiveTextColor
                        ),
                        modifier = Modifier.testTag("nav_team_metrics")
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { screen ->
            when (screen) {
                ReminderViewModel.Screen.EMPLOYEE_DASHBOARD -> {
                    EmployeeDashboardView(
                        viewModel = viewModel,
                        onSetWfoStatus = { activeWfoDialog = it }
                    )
                }
                ReminderViewModel.Screen.ADMIN_PORTAL -> {
                    AdminPortalView(viewModel = viewModel)
                }
                ReminderViewModel.Screen.TEAM_DIRECTORY -> {
                    TeamDirectoryView(viewModel = viewModel)
                }
                ReminderViewModel.Screen.COMPLIANCE_MATRIX -> {
                    ComplianceMatrixView(viewModel = viewModel)
                }
            }
        }
    }

    // Modal dialog for WFO declaration (visiting or not visiting)
    activeWfoDialog?.let { reminder ->
        Dialog(onDismissRequest = { activeWfoDialog = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "WFO Attendance Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Are you visiting the corporate office today?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Column of buttons for crisp selection
                    Button(
                        onClick = {
                            viewModel.completeTask(reminder.id, "Visiting Office")
                            activeWfoDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("wfo_visiting_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CorporateFare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Visiting Office", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.completeTask(reminder.id, "Working Remotely (Not Visiting)")
                            activeWfoDialog = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("wfo_not_visiting_btn"),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Not Visiting (Remote)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { activeWfoDialog = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    pendingAppUpdateTask?.let { task ->
        val updateUrl = remember(task.description) {
            val urlRegex = "https?://\\S+".toRegex()
            val match = urlRegex.find(task.description ?: "")
            match?.value ?: "https://drive.google.com/drive/folders/1Im5CEoPEHDHXfnLi-6UdJyzp-U_p4B4y?usp=sharing"
        }
        val displayDescription = remember(task.description) {
            var desc = task.description ?: ""
            // Remove any HTTP or HTTPS links
            val urlRegex = "https?://\\S+".toRegex()
            desc = desc.replace(urlRegex, "")
            // Remove any leftover introduction phrases regarding links
            desc = desc.replace("(?i)Updated app can be found in this link\\s*:\\s*".toRegex(), "")
            desc = desc.replace("(?i)Updated app can be found in this link".toRegex(), "")
            desc = desc.trim()
            if (desc.isEmpty()) {
                "Please configure and run the updated application setup on your system."
            } else {
                desc
            }
        }

        AlertDialog(
            onDismissRequest = { /* No-op to lock the dialog from being dismissed */ },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "App Update Required",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Urgent App Update Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "An administrator has requested an urgent setup update for the application.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Required Setup Instructions:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = displayDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "This setup must be completed immediately. Normal actions are locked until upgraded.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Button(
                    onClick = {
                        try {
                            uriHandler.openUri(updateUrl)
                        } catch (e: java.lang.Exception) {
                            // Safe fallback
                        }
                        val taskDateStr = task.startDay?.ifBlank { "2026-05-20" } ?: "2026-05-20"
                        viewModel.forceCompleteTask(task.id, taskDateStr)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("popup_update_confirm_btn")
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download & Complete Setup", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        )
    }
    }
    }

    if (isDbLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) {}
                .testTag("db_loading_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = dbLoadingMessage ?: "Processing, please wait...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun EmployeeDashboardView(viewModel: ReminderViewModel, onSetWfoStatus: (Reminder) -> Unit) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val activeReminders by viewModel.activeRemindersForSelectedDay.collectAsStateWithLifecycle()
    val activeCompletions by viewModel.activeCompletionsMap.collectAsStateWithLifecycle()
    val stats by viewModel.todayStats.collectAsStateWithLifecycle(ReminderViewModel.TaskStats(0, 0))

    val todayIndex = viewModel.daysOfWeek.indexOf(viewModel.todayName).coerceAtLeast(0)
    val selectedIndex = viewModel.daysOfWeek.indexOf(selectedDay).coerceAtLeast(0)
    val isPastDay = selectedIndex < todayIndex
    val isFutureDay = selectedIndex > todayIndex
    val isEmployee = currentUser?.role != "ADMIN" && currentUser?.role != "LEAD"
    val holidayName = viewModel.getHolidayNameForDay(selectedDay)
    val isHoliday = holidayName != null
    val isPastDisabled = isPastDay || isFutureDay || isHoliday

    var currentWallTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            currentWallTime = System.currentTimeMillis()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedDay) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        val days = viewModel.daysOfWeek
                        val currentIndex = days.indexOf(selectedDay).coerceAtLeast(0)
                        if (totalDrag < -100f) {
                            // Swiped Left -> Go to next day
                            if (currentIndex < days.lastIndex) {
                                viewModel.setSimulatedDay(days[currentIndex + 1])
                            }
                        } else if (totalDrag > 100f) {
                            // Swiped Right -> Go to previous day
                            if (currentIndex > 0) {
                                viewModel.setSimulatedDay(days[currentIndex - 1])
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    }
                )
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and Date Display card with professional polish styling
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val isToday = selectedDay == viewModel.todayName
                        val displaySelectedDayName = getDisplayDayName(selectedDay, viewModel.todayName, viewModel.daysOfWeek)
                        Text(
                            text = if (isToday) "$displaySelectedDayName, TODAY" else "$displaySelectedDayName, VIEWING DATE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val pendingCount = stats.total - stats.completed
                        Text(
                            text = if (pendingCount > 0) "$pendingCount Tasks Pending" else "All Tasks Checked Off",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Role: ${currentUser?.role ?: "EMPLOYEE"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = stats.percentage,
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        Text(
                            text = "${(stats.percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // Reminders title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOUR ACTIVE DUTIES TODAY",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SuggestionChip(
                    onClick = { },
                    label = { Text("${activeReminders.size} Active") }
                )
            }
        }

        if (isHoliday) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("holiday_locked_warning_banner"),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Holiday Locked",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "${holidayName} (Public Holiday)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Today is a corporate holiday. All task completions and modifications are disabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        } else if (isPastDisabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("past_day_locked_warning_banner"),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Day Locked",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            val titleText = if (isFutureDay) "Compliance Window Not Open" else "Compliance Window Closed"
                            val subtitleText = if (isFutureDay) "Future day. Compliance modification is not allowed yet." else "Backdated day. Compliance modification is disabled."
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // List of Active Reminders
        if (activeReminders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isEmptyTitle = if (isHoliday) "Enjoy your Holiday!" else "No Pending Required Tasks"
                        val isEmptyText = if (isHoliday) "Awesome! Today is a corporate holiday ($holidayName). Breathe easy and take the day off!" else "No core reminders assigned for this day."
                        val emptyIcon = if (isHoliday) Icons.Default.Star else Icons.Default.DoneAll
                        val emptyIconColor = if (isHoliday) MaterialTheme.colorScheme.primary else Color(0xFF2E7D32)

                        Icon(
                            imageVector = emptyIcon,
                            contentDescription = null,
                            tint = emptyIconColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = isEmptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = isEmptyText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            items(activeReminders) { reminder ->
                val completion = activeCompletions[reminder.id]
                val isCompleted = completion != null

                val isCurrentlySnoozed = false

                val itemIcon = when {
                    reminder.title.contains("Exception", ignoreCase = true) -> Icons.Default.Warning
                    reminder.title.contains("Timesheet", ignoreCase = true) -> Icons.Default.Timer
                    reminder.title.contains("WFO", ignoreCase = true) || reminder.title.contains("Attendance", ignoreCase = true) || reminder.description.contains("WFO Status", ignoreCase = true) -> Icons.Default.LocationOn
                    else -> Icons.Default.TaskAlt
                }

                val isDark = isSystemInDarkTheme()
                val cardBg = when {
                    isCompleted -> if (isDark) Color(0xFF112D19) else Color(0xFFF1F8E9)
                    isCurrentlySnoozed -> if (isDark) Color(0xFF2D1F10) else Color(0xFFFFF8E1)
                    else -> if (isDark) Color(0xFF222431) else Color.White
                }
                val cardBorderColor = when {
                    isCompleted -> if (isDark) Color(0xFF2E7D32) else Color(0xFF81C784)
                    isCurrentlySnoozed -> if (isDark) Color(0xFFE65100) else Color(0xFFFFD54F)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.5f else 0.3f)
                }
                val titleColor = when {
                    isCompleted -> if (isDark) Color(0xFFC8E6C9) else Color(0xFF1B5E20)
                    isCurrentlySnoozed -> if (isDark) Color(0xFFFFCC80) else Color(0xFF5D4037)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val descriptionColor = when {
                    isCompleted -> if (isDark) Color(0xFFA5D6A7) else Color(0xFF43A047)
                    isCurrentlySnoozed -> if (isDark) Color(0xFFFFB74D) else Color(0xFF8D6E63)
                    else -> MaterialTheme.colorScheme.secondary
                }
                val iconBgColor = when {
                    isCompleted -> if (isDark) Color(0xFF1B3B22) else Color(0xFFC8E6C9)
                    isCurrentlySnoozed -> if (isDark) Color(0xFF3E2723) else Color(0xFFFFECB3)
                    else -> if (isDark) Color(0xFF1D1F2B) else Color(0xFFF0F0F7)
                }
                val iconTint = when {
                    isCompleted -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                    isCurrentlySnoozed -> if (isDark) Color(0xFFFFB74D) else Color(0xFFF57F17)
                    else -> if (isDark) Color(0xFF9EA3B2) else Color(0xFF44474E)
                }
                val completionDetailsBg = if (isDark) Color(0xFF1B3B22) else Color(0xFFC8E6C9)
                val completionDetailsTextColor = if (isDark) Color(0xFFC8E6C9) else Color(0xFF1B5E20)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_card_${reminder.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = cardBg
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isCompleted || isCurrentlySnoozed) 1.5.dp else 1.dp,
                        color = cardBorderColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Title and Badge row with specialized icon box
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Rounded icon placeholder matching polished layout
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = iconBgColor,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentlySnoozed) Icons.Default.AccessTime else itemIcon,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = reminder.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = titleColor
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val urlPattern = remember { Regex("""(https?://[^\s]+)""") }
                                    val matchResult = remember(reminder.description) { urlPattern.find(reminder.description) }
                                    if (matchResult != null) {
                                        val url = matchResult.value
                                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                        val cleanText = reminder.description.replace(url, "").trim()
                                        if (cleanText.isNotEmpty()) {
                                            Text(
                                                text = cleanText,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = descriptionColor
                                                ),
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        Row(
                                            modifier = Modifier
                                                .clickable { uriHandler.openUri(url) }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = "Open Link",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Download/Update App",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                                )
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = reminder.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = descriptionColor
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Frequency Badges
                            val displayFrequency = if (!reminder.isRepetitive) "ONETIME" else reminder.frequency
                            val displayFrequencyText = if (displayFrequency == "FRIDAY" && !selectedDay.endsWith("Friday")) {
                                "FRIDAY (FALLBACK)"
                            } else {
                                displayFrequency
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when (displayFrequency) {
                                        "DAILY" -> MaterialTheme.colorScheme.primaryContainer
                                        "FRIDAY" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        "TUE_THU" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        "ONETIME" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = displayFrequencyText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (displayFrequency) {
                                        "DAILY" -> MaterialTheme.colorScheme.primary
                                        "FRIDAY" -> MaterialTheme.colorScheme.primary
                                        "TUE_THU" -> MaterialTheme.colorScheme.error
                                        "ONETIME" -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.secondary
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Completion Detail payload log
                        if (isCompleted) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = completionDetailsBg,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = completionDetailsTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = completion.payload ?: "Acknowledged and submitted.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = completionDetailsTextColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Interactive Controls based on task model
                        // Interactive Controls based on task model
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isCompleted) {
                                TextButton(
                                    onClick = { viewModel.undoCompletion(reminder.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    enabled = !isPastDisabled,
                                    modifier = Modifier.testTag("undo_button_${reminder.id}")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mark Not Completed")
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            when {
                                                (reminder.title.contains("WFO", ignoreCase = true) || reminder.title.contains("Attendance", ignoreCase = true) || reminder.description.contains("WFO Status", ignoreCase = true)) && !reminder.title.contains("Exception", ignoreCase = true) -> {
                                                    onSetWfoStatus(reminder)
                                                }
                                                else -> {
                                                    val defaultPayload = when {
                                                        reminder.title.contains("Timesheet", ignoreCase = true) -> "Completed"
                                                        reminder.title.contains("Exception", ignoreCase = true) -> "Completed"
                                                        else -> "Completed"
                                                    }
                                                    viewModel.completeTask(reminder.id, defaultPayload)
                                                }
                                            }
                                        },
                                        enabled = !isPastDisabled,
                                        modifier = Modifier.height(36.dp).testTag("complete_std_${reminder.id}"),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                (reminder.title.contains("WFO", ignoreCase = true) || reminder.title.contains("Attendance", ignoreCase = true)) && !reminder.title.contains("Exception", ignoreCase = true) -> Icons.Default.LocationOn
                                                else -> Icons.Default.Check
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when {
                                                (reminder.title.contains("WFO", ignoreCase = true) || reminder.title.contains("Attendance", ignoreCase = true)) && !reminder.title.contains("Exception", ignoreCase = true) -> "Set Status"
                                                else -> "Complete"
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Planned Leaves Tracker Section
        item {
            var showAddLeaveDialog by remember { mutableStateOf(false) }
            val allPlannedLeaves by viewModel.allPlannedLeaves.collectAsStateWithLifecycle()
            val currentViewDate = viewModel.dateMapping[selectedDay] ?: ""
            val myPlannedLeaves = allPlannedLeaves.filter { 
                it.userId == currentUser?.id && (currentViewDate.isEmpty() || it.endDate >= currentViewDate)
            }
            val isDark = isSystemInDarkTheme()

            val cardBg = if (isDark) Color(0xFF222431) else Color.White
            val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.5f else 0.3f)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .testTag("planned_leaves_container"),
                colors = CardDefaults.cardColors(
                    containerColor = cardBg
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val iconBgColor = if (isDark) Color(0xFF1D1F2B) else Color(0xFFF0F0F7)
                            val iconTint = if (isDark) Color(0xFF9EA3B2) else Color(0xFF44474E)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = iconBgColor,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = "Leaves Icon",
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Planned Leaves Tracker",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "Manage your scheduled time off",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { showAddLeaveDialog = true },
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("add_leave_button"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Plan Leave",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (myPlannedLeaves.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isDark) Color(0xFF1D1F2B) else Color(0xFFF9F9FB),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No upcoming planned leaves. Keep your team informed by planning your leaves ahead of time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            myPlannedLeaves.forEach { leave ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0xFF1D1F2B) else Color(0xFFF9F9FB)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.3f else 0.15f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "${leave.startDate} to ${leave.endDate}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                // Status badge
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            shape = RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = leave.status,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Reason: ${leave.reason}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deletePlannedLeave(leave) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Cancel Leave",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dialog to Add/Plan Leave
            if (showAddLeaveDialog) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val cal = java.util.Calendar.getInstance()
                val defaultStartDate = sdf.format(cal.time)
                cal.add(java.util.Calendar.DAY_OF_YEAR, 3) // default end date after 3 days
                val defaultEndDate = sdf.format(cal.time)

                var startDateStr by remember { mutableStateOf(defaultStartDate) }
                var endDateStr by remember { mutableStateOf(defaultEndDate) }
                var leaveReason by remember { mutableStateOf("") }
                var dateErrorMessage by remember { mutableStateOf<String?>(null) }

                fun showDatePicker(isStartDate: Boolean) {
                    val currentVal = if (isStartDate) startDateStr else endDateStr
                    val parseCal = java.util.Calendar.getInstance()
                    try {
                        val parsed = sdf.parse(currentVal)
                        if (parsed != null) {
                            parseCal.time = parsed
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                    val y = parseCal.get(java.util.Calendar.YEAR)
                    val m = parseCal.get(java.util.Calendar.MONTH)
                    val d = parseCal.get(java.util.Calendar.DAY_OF_MONTH)

                    val picker = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val formatted = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                            if (isStartDate) {
                                startDateStr = formatted
                            } else {
                                endDateStr = formatted
                            }
                        },
                        y, m, d
                    )

                    // Disable last dates (past dates)
                    val minCal = java.util.Calendar.getInstance()
                    minCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    minCal.set(java.util.Calendar.MINUTE, 0)
                    minCal.set(java.util.Calendar.SECOND, 0)
                    minCal.set(java.util.Calendar.MILLISECOND, 0)
                    picker.datePicker.minDate = minCal.timeInMillis

                    // Only enable next 3 months
                    val maxCal = java.util.Calendar.getInstance()
                    maxCal.add(java.util.Calendar.MONTH, 3)
                    picker.datePicker.maxDate = maxCal.timeInMillis

                    picker.show()
                }

                Dialog(onDismissRequest = { showAddLeaveDialog = false }) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Plan Upcoming Leave",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Clickable Start Date field
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clickable { showDatePicker(true) }
                            ) {
                                OutlinedTextField(
                                    value = startDateStr,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Start Date") },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Event,
                                            contentDescription = "Select Start Date"
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            // Clickable End Date field
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clickable { showDatePicker(false) }
                            ) {
                                OutlinedTextField(
                                    value = endDateStr,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("End Date") },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Event,
                                            contentDescription = "Select End Date"
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            OutlinedTextField(
                                value = leaveReason,
                                onValueChange = { leaveReason = it },
                                label = { Text("Reason for Leave") },
                                placeholder = { Text("Describe the reason...") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            if (dateErrorMessage != null) {
                                Text(
                                    text = dateErrorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showAddLeaveDialog = false },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Discard")
                                }

                                Button(
                                    onClick = {
                                        if (leaveReason.isBlank()) {
                                            dateErrorMessage = "Reason description cannot be empty"
                                        } else {
                                            try {
                                                val start = sdf.parse(startDateStr)
                                                val end = sdf.parse(endDateStr)
                                                if (start != null && end != null && end.before(start)) {
                                                    dateErrorMessage = "End Date cannot be before Start Date"
                                                } else {
                                                    viewModel.addPlannedLeave(startDateStr, endDateStr, leaveReason)
                                                    showAddLeaveDialog = false
                                                }
                                            } catch (e: Exception) {
                                                dateErrorMessage = "Invalid dates selected"
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Set Leave")
                                }
                            }
                        }
                    }
                }
            }
        }
    }



}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionIcon: ImageVector? = null,
    actionIconContentDescription: String? = null,
    onActionClick: (() -> Unit)? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (extraContent != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    extraContent()
                }
            }
            if (actionIcon != null && onActionClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onActionClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = actionIconContentDescription ?: "Header Action",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdminPortalView(viewModel: ReminderViewModel) {
    val allReminders by viewModel.allReminders.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()


    var taskTitle by remember { mutableStateOf("") }
    var taskDescription by remember { mutableStateOf("") }
    var taskFreq by remember { mutableStateOf("DAILY") } // "DAILY", "FRIDAY", "TUE_THU", "CUSTOM"
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    var isRepetitive by remember { mutableStateOf(true) }
    var oneTimeTargetDay by remember { mutableStateOf("") }
    
    // Custom Day Selection state
    val availableDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
    val selectedCustomDays = remember { mutableStateListOf<String>() }

    // Targeting state
    val selectedTargetUserIds = remember { mutableStateListOf<String>() } // empty = All Employees

    var isAddingNew by remember { mutableStateOf(false) }
    var reminderToDelete by remember { mutableStateOf<com.example.data.Reminder?>(null) }
    var showAppUpdateDialog by remember { mutableStateOf(false) }


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                title = "Corporate Administrator Panel",
                subtitle = "Deploy custom workflow and reminder guidelines to employees dynamically.",
                icon = Icons.Default.AdminPanelSettings
            )
        }

        // Toggle Expand New Reminder form
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE COMPANY TASKS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { isAddingNew = !isAddingNew },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAddingNew) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_reminder_toggle")
                ) {
                    Icon(
                        imageVector = if (isAddingNew) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAddingNew) "Collapse" else "Add New",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Add Reminder Expandable Form
        if (isAddingNew) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_reminder_form"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Configure Workflow Reminder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = taskTitle,
                            onValueChange = { taskTitle = it },
                            label = { Text("Task Title") },
                            placeholder = { Text("e.g. Complete Security Quiz") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = taskDescription,
                            onValueChange = { taskDescription = it },
                            label = { Text("Detailed Description / Mandate") },
                            placeholder = { Text("Explain what employee should perform...") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Task Type Selection (Repetitive vs One-Time)
                        Text(text = "Task Execution Type:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Repetitive option card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isRepetitive = true },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isRepetitive) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isRepetitive) 2.dp else 1.dp,
                                    color = if (isRepetitive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = isRepetitive,
                                        onClick = { isRepetitive = true },
                                        modifier = Modifier.testTag("task_type_repetitive")
                                    )
                                    Column {
                                        Text("Repetitive Task", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Appears in all upcoming weeks based on frequency schedule", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                    }
                                }
                            }

                            // One-Time option card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isRepetitive = false },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!isRepetitive) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (!isRepetitive) 2.dp else 1.dp,
                                    color = if (!isRepetitive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = !isRepetitive,
                                        onClick = { isRepetitive = false },
                                        modifier = Modifier.testTag("task_type_onetime")
                                    )
                                    Column {
                                        val todayIdx = viewModel.daysOfWeek.indexOf(viewModel.todayName).coerceAtLeast(0)
                                        val selectedIdx = viewModel.daysOfWeek.indexOf(selectedDay).coerceAtLeast(0)
                                        val initialTargetDay = if (oneTimeTargetDay.isNotBlank()) oneTimeTargetDay else {
                                            if (selectedIdx < todayIdx) viewModel.todayName else selectedDay
                                        }
                                        val initialDateStr = if (oneTimeTargetDay.contains("-")) oneTimeTargetDay else (viewModel.dateMapping[oneTimeTargetDay] ?: (viewModel.dateMapping[initialTargetDay] ?: ""))
                                        val initialFormattedDate = try {
                                            val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                            val formatter = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                                            parser.parse(initialDateStr)?.let { formatter.format(it) } ?: initialDateStr
                                        } catch (e: java.lang.Exception) {
                                            initialDateStr
                                        }
                                        val initialDisplayDay = viewModel.dateMapping.entries.firstOrNull { it.value == initialDateStr }?.key ?: "Selected Date"
                                        val dayLabelText = if (initialDisplayDay != "Selected Date") getDisplayDayName(initialDisplayDay, viewModel.todayName, viewModel.daysOfWeek) else "Selected Date"
                                        val displayLabel = if (initialFormattedDate.isNotEmpty()) "$dayLabelText ($initialFormattedDate)" else initialFormattedDate
                                        Text("One-Time Task", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Only active on selected target date ($displayLabel)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }

                        if (!isRepetitive) {
                            val todayIndex = viewModel.daysOfWeek.indexOf(viewModel.todayName).coerceAtLeast(0)
                            val selectedIdx = viewModel.daysOfWeek.indexOf(selectedDay).coerceAtLeast(0)
                            val targetDate = if (oneTimeTargetDay.isNotBlank()) {
                                if (oneTimeTargetDay.contains("-")) oneTimeTargetDay else (viewModel.dateMapping[oneTimeTargetDay] ?: "")
                            } else {
                                val defaultDay = if (selectedIdx < todayIndex) viewModel.todayName else selectedDay
                                viewModel.dateMapping[defaultDay] ?: ""
                            }
                            val getDayLabelForDate = { dateStr: String ->
                                val formattedDate = try {
                                    val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    val formatter = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
                                    parser.parse(dateStr)?.let { formatter.format(it) } ?: dateStr
                                } catch (e: java.lang.Exception) {
                                    dateStr
                                }
                                val dayName = viewModel.dateMapping.entries.firstOrNull { it.value == dateStr }?.key ?: "Selected Date"
                                val displayDay = if (dayName != "Selected Date") getDisplayDayName(dayName, viewModel.todayName, viewModel.daysOfWeek) else "Selected Date"
                                if (formattedDate.isNotEmpty()) "$displayDay ($formattedDate)" else displayDay
                            }

                            Text(
                                text = "Select Target Calendar Date (Simulation Day):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            SimulationCalendarPicker(
                                selectedDateStr = targetDate,
                                onDateSelected = { dateStr -> oneTimeTargetDay = dateStr },
                                dateMapping = viewModel.dateMapping,
                                daysOfWeek = viewModel.daysOfWeek,
                                todayName = viewModel.todayName
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Since this is a one-time task, it will display ONLY on the selected calendar date (${getDayLabelForDate(targetDate)}) and will not repeat.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        } else {
                            // Frequency Picker
                            Text(text = "Recurring Schedule Frequency:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("DAILY", "FRIDAY", "TUE_THU", "CUSTOM").forEach { freq ->
                                    FilterChip(
                                        selected = taskFreq == freq,
                                        onClick = {
                                            taskFreq = freq
                                            selectedCustomDays.clear()
                                        },
                                        label = { Text(freq, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            // Custom Workday selector (Only shows up when CUSTOM is selected)
                            if (taskFreq == "CUSTOM") {
                                Text(text = "Target Simulation Day of Week:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    availableDays.forEach { dayName ->
                                        val isSelected = selectedCustomDays.contains(dayName)
                                        InputChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (isSelected) selectedCustomDays.remove(dayName)
                                                else selectedCustomDays.add(dayName)
                                            },
                                            label = { Text(dayName) }
                                        )
                                    }
                                }
                            }
                        }

                        // Target employee field (dropdown simulation list)
                        Text(text = "Target Audience Selection (Select multiple, or broadcast to all):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Option 1: All
                            FilterChip(
                                selected = selectedTargetUserIds.isEmpty(),
                                onClick = { selectedTargetUserIds.clear() },
                                label = { Text("🏢 Broadcast All Employees") }
                            )

                            // Load individual users (Exclude Admin themselves generally)
                            allUsers.filter { it.role != "ADMIN" }.forEach { employee ->
                                val isEmployeeSelected = selectedTargetUserIds.contains(employee.id)
                                FilterChip(
                                    selected = isEmployeeSelected,
                                    onClick = {
                                        if (isEmployeeSelected) {
                                            selectedTargetUserIds.remove(employee.id)
                                        } else {
                                            selectedTargetUserIds.add(employee.id)
                                        }
                                    },
                                    label = { Text("👤 ${employee.name}") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (taskTitle.isNotBlank() && taskDescription.isNotBlank()) {
                                    val finalStartDay = if (!isRepetitive) {
                                        if (oneTimeTargetDay.isNotBlank()) {
                                            oneTimeTargetDay
                                        } else {
                                            val todayIdx = viewModel.daysOfWeek.indexOf(viewModel.todayName).coerceAtLeast(0)
                                            val selectedIdx = viewModel.daysOfWeek.indexOf(selectedDay).coerceAtLeast(0)
                                            val defaultDay = if (selectedIdx < todayIdx) viewModel.todayName else selectedDay
                                            viewModel.dateMapping[defaultDay] ?: defaultDay
                                        }
                                    } else {
                                        selectedDay
                                    }
                                    val targetUserIdString = if (selectedTargetUserIds.isEmpty()) null else selectedTargetUserIds.joinToString(",")
                                    viewModel.addNewReminder(
                                        title = taskTitle,
                                        description = taskDescription,
                                        frequency = if (isRepetitive) taskFreq else "ONETIME",
                                        customDays = if (isRepetitive && taskFreq == "CUSTOM") selectedCustomDays.toList() else null,
                                        targetUserId = targetUserIdString,
                                        isRepetitive = isRepetitive,
                                        startDay = finalStartDay
                                    )
                                    // Reset fields
                                    taskTitle = ""
                                    taskDescription = ""
                                    taskFreq = "DAILY"
                                    isRepetitive = true
                                    oneTimeTargetDay = ""
                                    selectedCustomDays.clear()
                                    selectedTargetUserIds.clear()
                                    isAddingNew = false
                                }
                            },
                            enabled = taskTitle.isNotBlank() && taskDescription.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("publish_reminder_btn")
                        ) {
                            Icon(Icons.Default.Publish, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publish Dynamic Reminder")
                        }
                    }
                }
            }
        }

        // List of Active Reminders in Database
        items(allReminders) { reminder ->
            val targetEmployees = if (reminder.targetUserId.isNullOrEmpty()) {
                emptyList()
            } else {
                val targetIds = reminder.targetUserId.split(",").map { it.trim() }
                allUsers.filter { targetIds.contains(it.id) }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = reminder.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (reminder.isSystemDefault) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "SYSTEM",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = reminder.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Schedule & target detail chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!reminder.isRepetitive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (!reminder.isRepetitive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Freq: ${if (!reminder.isRepetitive) "ONETIME" else reminder.frequency}${if (reminder.isRepetitive && reminder.frequency == "CUSTOM") " (${reminder.customDays})" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (targetEmployees.isNotEmpty()) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (targetEmployees.isNotEmpty()) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (targetEmployees.isNotEmpty()) {
                                        "Target: ${targetEmployees.joinToString(", ") { it.name }}"
                                    } else {
                                        "Target: Broadcast All Employees"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            if (!reminder.isRepetitive) {
                                val rawDate = viewModel.dateMapping[reminder.startDay]
                                val formattedDate = if (!rawDate.isNullOrEmpty()) {
                                    try {
                                        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                        val formatter = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
                                        parser.parse(rawDate)?.let { formatter.format(it) } ?: rawDate
                                    } catch (e: java.lang.Exception) {
                                        rawDate
                                    }
                                } else {
                                    ""
                                }
                                val targetDateLabel = if (formattedDate.isNotEmpty()) {
                                    val displayDay = getDisplayDayName(reminder.startDay ?: "", viewModel.todayName, viewModel.daysOfWeek)
                                    "$displayDay ($formattedDate)"
                                } else {
                                    reminder.startDay ?: ""
                                }
                                if (targetDateLabel.isNotEmpty()) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Target Date: $targetDateLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Delete customizable reminder button (Admin privilege)
                    if (!reminder.isSystemDefault) {
                        IconButton(
                            onClick = { reminderToDelete = reminder },
                            modifier = Modifier.testTag("delete_reminder_${reminder.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

    }

    if (reminderToDelete != null) {
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(text = "Confirm Deletion of Task") },
            text = { Text(text = "Are you sure you want to delete the task \"${reminderToDelete?.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        reminderToDelete?.let { viewModel.deleteReminder(it) }
                        reminderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_reminder_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { reminderToDelete = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAppUpdateDialog) {
        val todayStr = viewModel.dateMapping[selectedDay] ?: viewModel.todayName
        AlertDialog(
            onDismissRequest = { showAppUpdateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Broadcast App Update",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "This will trigger an urgent notification and assign a one-time task to all employees (including leads) for the current day to update their application setup.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "TASK PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "Title: Update Corporate Task App",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Details: Please configure and run the updated application setup on your system.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Target: All Employees & Leads",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Simulated Date: $todayStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalStartDay = viewModel.dateMapping[selectedDay] ?: selectedDay
                        viewModel.broadcastAppUpdate(
                            title = "Update Corporate Task App",
                            description = "Please configure and run the updated application setup on your system. Updated app can be found in this link : https://drive.google.com/drive/folders/1Im5CEoPEHDHXfnLi-6UdJyzp-U_p4B4y?usp=sharing",
                            frequency = "ONETIME",
                            customDays = null,
                            targetUserId = null,
                            isRepetitive = false,
                            startDay = finalStartDay
                        )
                        showAppUpdateDialog = false
                    },
                    modifier = Modifier.testTag("confirm_broadcast_update_btn")
                ) {
                    Text("Confirm & Send")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAppUpdateDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TeamDirectoryView(viewModel: ReminderViewModel) {
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isAdmin = currentUser?.role == "ADMIN"

    val context = androidx.compose.ui.platform.LocalContext.current
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importTeammatesFromUri(context, it)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    val csvContent = """emp name,emp id
Alice Vance,emp001
Bob Hudson,emp002
Charlie Sterling,emp003
Diana Prince,emp004
Ethan Hunt,emp005
Fiona Gallagher,emp006
George Clark,emp007
Hannah Abbott,emp008
Ian Malcolm,emp009
Julia Roberts,emp010"""
                    outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                android.widget.Toast.makeText(context, "Sample CSV saved successfully!", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to save sample: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    if (importStatus != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportStatus() },
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isPending = importStatus?.contains("Importing") == true
                    val isError = importStatus?.contains("Error") == true
                    if (isPending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (isError) {
                        Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF2E7D32))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPending) "Importing Teammates" else if (isError) "Import Failed" else "Import Success",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = importStatus ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                val isPending = importStatus?.contains("Importing") == true
                if (!isPending) {
                    Button(
                        onClick = { viewModel.clearImportStatus() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }

    // User Directory states
    var isAddingUser by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    var newUserId by remember { mutableStateOf("") }
    var newUserRole by remember { mutableStateOf("EMPLOYEE") } // "EMPLOYEE", "ADMIN"
    var userErrorMsg by remember { mutableStateOf<String?>(null) }
    var userSuccessMsg by remember { mutableStateOf<String?>(null) }

    val selectedUserIds = remember { mutableStateListOf<String>() }
    var userToDelete by remember { mutableStateOf<User?>(null) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                title = "Team Member Directory",
                subtitle = "Register team profiles, configure functional roles, and run bulk actions or CSV imports.",
                icon = Icons.Default.People
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TEAM MEMBER DIRECTORY",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (isAdmin) {
                    Button(
                        onClick = { 
                            isAddingUser = !isAddingUser
                            userErrorMsg = null
                            userSuccessMsg = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAddingUser) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_user_toggle")
                    ) {
                        Icon(
                            imageVector = if (isAddingUser) Icons.Default.Close else Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAddingUser) "Collapse" else "Add User",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Expandable Add New User Form
        if (isAddingUser && (currentUser?.role == "ADMIN")) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_user_form"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Register Corporate User Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        if (userErrorMsg != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(text = userErrorMsg ?: "", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        if (userSuccessMsg != null) {
                            Surface(
                                color = Color(0xFFE8F5E9),
                                contentColor = Color(0xFF2E7D32),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(text = userSuccessMsg ?: "", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newUserName,
                            onValueChange = { newUserName = it; userErrorMsg = null; userSuccessMsg = null },
                            label = { Text("Full Name") },
                            placeholder = { Text("e.g. Jane Doe") },
                            modifier = Modifier.fillMaxWidth().testTag("admin_new_name"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = newUserId,
                            onValueChange = { newUserId = it; userErrorMsg = null; userSuccessMsg = null },
                            label = { Text("Unique User ID (Credential Key)") },
                            placeholder = { Text("e.g. jane.doe or emp_105") },
                            modifier = Modifier.fillMaxWidth().testTag("admin_new_id"),
                            singleLine = true
                        )

                        // Role Selectors
                        Text(text = "Functional Role:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = newUserRole == "EMPLOYEE",
                                onClick = { newUserRole = "EMPLOYEE" },
                                label = { Text("Employee") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = newUserRole == "LEAD",
                                onClick = { newUserRole = "LEAD" },
                                label = { Text("Lead") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = newUserRole == "ADMIN",
                                onClick = { newUserRole = "ADMIN" },
                                label = { Text("Admin") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                val cleanId = newUserId.trim().lowercase()
                                val exists = allUsers.any { it.id == cleanId }
                                if (newUserName.isBlank() || newUserId.isBlank()) {
                                    userErrorMsg = "Please fill in all profile fields."
                                } else if (exists) {
                                    userErrorMsg = "User ID already exists. Choose a unique one."
                                } else {
                                    val success = viewModel.addUser(
                                        name = newUserName,
                                        userId = newUserId,
                                        role = newUserRole
                                    )
                                    if (success) {
                                        userSuccessMsg = "User \"$newUserName\" successfully registered!"
                                        // Reset fields
                                        newUserName = ""
                                        newUserId = ""
                                        newUserRole = "EMPLOYEE"
                                    } else {
                                        userErrorMsg = "Unable to create user profile."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("admin_save_user_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create User Profile")
                        }
                    }
                }
            }
        }

        if (isAdmin) {
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("teammate_import_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = "Import icon",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Import Teammates",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Populate employees via XLSX or CSV",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "💡 Formatting Guide:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "• The sheet should have 2 columns labeled: emp name and emp id.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Imported team members will default to the Employee role.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• All Excel formatting, CSV formulas, and text quotes are supported.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_file_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Upload Icon",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { createDocumentLauncher.launch("sample_teammates.csv") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("download_sample_csv_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Icon",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Get Template", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Select All / Selection control bar
        if (isAdmin) {
            item {
                val selectableUsers = allUsers.filter { it.id != currentUser?.id }
            if (selectableUsers.isNotEmpty()) {
                val allSelected = selectedUserIds.size == selectableUsers.size && selectableUsers.isNotEmpty()
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selection_control_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable {
                                    val shouldSelectAll = !allSelected
                                    selectedUserIds.clear()
                                    if (shouldSelectAll) {
                                        selectedUserIds.addAll(selectableUsers.map { it.id })
                                    }
                                }
                            ) {
                                Checkbox(
                                    checked = allSelected,
                                    onCheckedChange = { checked ->
                                        selectedUserIds.clear()
                                        if (checked) {
                                            selectedUserIds.addAll(selectableUsers.map { it.id })
                                        }
                                    },
                                    modifier = Modifier.testTag("select_all_checkbox")
                                )
                                Text(
                                    text = "Select All Team Members (${selectableUsers.size})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            if (selectedUserIds.isNotEmpty()) {
                                TextButton(
                                    onClick = { selectedUserIds.clear() },
                                    modifier = Modifier.testTag("clear_selection_btn")
                                ) {
                                    Text(
                                        text = "Clear (${selectedUserIds.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (selectedUserIds.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Bulk Actions (${selectedUserIds.size} Selected):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Make Admin
                                    Button(
                                        onClick = {
                                            viewModel.updateUsersRoles(selectedUserIds.toList(), "ADMIN")
                                            selectedUserIds.clear()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("bulk_make_admin_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Admin",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Make Employee
                                    Button(
                                        onClick = {
                                            viewModel.updateUsersRoles(selectedUserIds.toList(), "LEAD")
                                            selectedUserIds.clear()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("bulk_make_lead_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Group,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Lead",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Make Employee
                                    Button(
                                        onClick = {
                                            viewModel.updateUsersRoles(selectedUserIds.toList(), "EMPLOYEE")
                                            selectedUserIds.clear()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .testTag("bulk_make_employee_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Member",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Delete
                                    Button(
                                        onClick = {
                                            showBulkDeleteConfirmation = true
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .testTag("bulk_delete_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Delete",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // List showing all users registered
        items(allUsers) { user ->
            val isCurrentUser = user.id == currentUser?.id
            val isSelected = selectedUserIds.contains(user.id)
            var showRoleDropdown by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isAdmin && !isCurrentUser) {
                        if (isSelected) {
                            selectedUserIds.remove(user.id)
                        } else {
                            selectedUserIds.add(user.id)
                        }
                    }
                    .testTag("user_card_${user.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAdmin && !isCurrentUser) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selectedUserIds.contains(user.id)) selectedUserIds.add(user.id)
                                    } else {
                                        selectedUserIds.remove(user.id)
                                    }
                                },
                                modifier = Modifier.testTag("checkbox_user_${user.id}")
                            )
                        } else if (isCurrentUser) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Current user indicator",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isCurrentUser) "${user.name} (You)" else user.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Role Badge
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (user.role) {
                                            "ADMIN" -> MaterialTheme.colorScheme.primaryContainer
                                            "LEAD" -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        },
                                        contentColor = when (user.role) {
                                            "ADMIN" -> MaterialTheme.colorScheme.onPrimaryContainer
                                            "LEAD" -> MaterialTheme.colorScheme.onTertiaryContainer
                                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = user.role,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                Text(
                                    text = "User ID: ${user.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isAdmin) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick toggle role button (Simplified to Dropdown selection)
                            if (!isCurrentUser) {
                                Box {
                                    IconButton(
                                        onClick = { showRoleDropdown = true },
                                        modifier = Modifier.testTag("toggle_role_user_${user.id}")
                                    ) {
                                        Icon(
                                            imageVector = when (user.role) {
                                                "ADMIN" -> Icons.Default.AdminPanelSettings
                                                "LEAD" -> Icons.Default.Group
                                                else -> Icons.Default.Person
                                            },
                                            contentDescription = "Switch user role",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showRoleDropdown,
                                        onDismissRequest = { showRoleDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Admin") },
                                            onClick = {
                                                viewModel.updateUserRole(user.id, "ADMIN")
                                                showRoleDropdown = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                            },
                                            modifier = Modifier.testTag("role_menu_admin_${user.id}")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Lead") },
                                            onClick = {
                                                viewModel.updateUserRole(user.id, "LEAD")
                                                showRoleDropdown = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                            },
                                            modifier = Modifier.testTag("role_menu_lead_${user.id}")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Employee") },
                                            onClick = {
                                                viewModel.updateUserRole(user.id, "EMPLOYEE")
                                                showRoleDropdown = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                            },
                                            modifier = Modifier.testTag("role_menu_employee_${user.id}")
                                        )
                                    }
                                }
                            }

                            // Delete button (Cannot delete yourself)
                            if (!isCurrentUser) {
                                IconButton(
                                    onClick = { userToDelete = user },
                                    modifier = Modifier.testTag("delete_user_${user.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete User", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Delete User Profile?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the user profile of '${userToDelete?.name}'? This action cannot be undone and will remove all their local system associations.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        userToDelete?.let {
                            viewModel.deleteUser(it)
                        }
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_delete_user_btn")
                ) {
                    Text("Delete User")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { userToDelete = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBulkDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmation = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Delete Selected Users?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete ${selectedUserIds.size} selected user profile(s)? This action will delete these user accounts permanently.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUsers(selectedUserIds.toList())
                        selectedUserIds.clear()
                        showBulkDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_bulk_delete_user_btn")
                ) {
                    Text("Delete Users")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBulkDeleteConfirmation = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ComplianceMatrixView(viewModel: ReminderViewModel) {
    val allReminders by viewModel.allReminders.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allCompletions by viewModel.allCompletions.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val allPlannedLeaves by viewModel.allPlannedLeaves.collectAsStateWithLifecycle()

    val dateStr = viewModel.dateMapping[selectedDay] ?: "2026-05-20"

    val todayIndex = viewModel.daysOfWeek.indexOf(viewModel.todayName).coerceAtLeast(0)
    val selectedIndex = viewModel.daysOfWeek.indexOf(selectedDay).coerceAtLeast(0)
    val isPastDay = selectedIndex < todayIndex
    val isFutureDay = selectedIndex > todayIndex
    val holidayName = viewModel.getHolidayNameForDay(selectedDay)
    val isHoliday = holidayName != null
    val isPastDisabled = isPastDay || isFutureDay || isHoliday

    // Group completions for the active simulated date
    val completionsForSelectedDate = allCompletions.filter { it.dateString == dateStr }

    var reportTextState by remember { mutableStateOf("") }
    var targetReportDateStr by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var rangeStartIdx by remember { mutableStateOf(0) }
    var rangeEndIdx by remember { mutableStateOf(13) }

    val todayDateStr = viewModel.dateMapping[viewModel.todayName] ?: "2026-05-24"
    val todayLocalDate = remember(todayDateStr) {
        try {
            LocalDate.parse(todayDateStr)
        } catch (e: Exception) {
            LocalDate.of(2026, 5, 24)
        }
    }
    var customStartDate by remember(todayLocalDate) { mutableStateOf(todayLocalDate.minusWeeks(1)) }
    var customEndDate by remember(todayLocalDate) { mutableStateOf(todayLocalDate) }

    fun generateRangeReportText(startIdx: Int, endIdx: Int): String {
        return buildString {
            appendLine("==============================================")
            appendLine("      CORPORATE COMPLIANCE & METRICS REPORT")
            appendLine("               (MULTI-DAY RANGE)")
            appendLine("==============================================")
            val daysList = viewModel.daysOfWeek
            val firstDay = daysList[startIdx]
            val lastDay = daysList[endIdx]
            val firstDate = viewModel.dateMapping[firstDay] ?: ""
            val lastDate = viewModel.dateMapping[lastDay] ?: ""
            appendLine("Date Range: $firstDate ($firstDay) to $lastDate ($lastDay)")
            appendLine("Generated At: 2026-05-24 (Local Sync)")
            appendLine("==============================================")
            appendLine()

            for (idx in startIdx..endIdx) {
                val targetDay = daysList[idx]
                val targetDateStr = viewModel.dateMapping[targetDay] ?: ""
                
                appendLine("----------------------------------------------")
                appendLine("DATE: $targetDateStr ($targetDay)")
                appendLine("----------------------------------------------")
                
                allUsers.filter { user -> user.role != "ADMIN" }.forEach { user ->
                    val activeTasksForUser = allReminders.filter { reminder ->
                        val userMatches = reminder.targetUserId.isNullOrEmpty() || reminder.targetUserId.split(",").map { it.trim() }.contains(user.id)
                        if (!userMatches) return@filter false

                        viewModel.isReminderActiveOnDay(reminder, targetDay)
                    }

                    val completionsForTargetDate = allCompletions.filter { it.dateString == targetDateStr }
                    val completedUserTasks = completionsForTargetDate.filter { it.userId == user.id }
                    val completedMapByReminderId = completedUserTasks.associateBy { it.reminderId }

                    val completedCount = activeTasksForUser.count { completedMapByReminderId.containsKey(it.id) }
                    val totalCount = activeTasksForUser.size
                    val hasCompleted = if (totalCount > 0) "${completedCount}/${totalCount}" else "N/A (No active tasks)"

                    val userLeaves = allPlannedLeaves.filter { it.userId == user.id && (targetDateStr.isEmpty() || it.endDate >= targetDateStr) }

                    appendLine("  EMPLOYEE: ${user.name} (ID: ${user.id})")
                    appendLine("  Task Status: $hasCompleted completed")
                    
                    if (activeTasksForUser.isNotEmpty()) {
                        appendLine("  Active Tasks details:")
                        activeTasksForUser.forEach { task ->
                            val taskCompletion = completedMapByReminderId[task.id]
                            val taskIsDone = taskCompletion != null
                            val statusStr = if (taskIsDone) {
                                "✓ COMPLETED" + (if (taskCompletion?.payload != null) " (${taskCompletion.payload})" else "")
                            } else {
                                "✗ PENDING"
                            }
                            appendLine("    - [${task.frequency}] ${task.title}: $statusStr")
                        }
                    } else {
                        appendLine("    (No assigned active tasks for this weekday)")
                    }
                    
                    appendLine("  Planned Leaves:")
                    if (userLeaves.isNotEmpty()) {
                        userLeaves.forEach { leave ->
                            appendLine("    - From ${leave.startDate} to ${leave.endDate} (${leave.status})")
                            appendLine("      Reason: ${leave.reason}")
                        }
                    } else {
                        appendLine("    - No scheduled planned leaves of absence.")
                    }
                    appendLine("  - - - - - - - - - - - - - - - - - - - - - -")
                }
                appendLine()
            }
            appendLine("==============================================")
        }
    }

    fun generateReportText(targetDay: String, targetDateStr: String): String {
        return buildString {
            appendLine("==============================================")
            appendLine("      CORPORATE COMPLIANCE & METRICS REPORT")
            appendLine("==============================================")
            appendLine("Report Date: $targetDateStr ($targetDay)")
            appendLine("Generated At: 2026-05-24 (Local Sync)")
            appendLine("==============================================")
            allUsers.filter { it.role != "ADMIN" }.forEach { user ->
                val activeTasksForUser = allReminders.filter { reminder ->
                    val userMatches = reminder.targetUserId.isNullOrEmpty() || reminder.targetUserId.split(",").map { it.trim() }.contains(user.id)
                    if (!userMatches) return@filter false

                    viewModel.isReminderActiveOnDay(reminder, targetDay)
                }

                val completionsForTargetDate = allCompletions.filter { it.dateString == targetDateStr }
                val completedUserTasks = completionsForTargetDate.filter { it.userId == user.id }
                val completedMapByReminderId = completedUserTasks.associateBy { it.reminderId }

                val completedCount = activeTasksForUser.count { completedMapByReminderId.containsKey(it.id) }
                val totalCount = activeTasksForUser.size
                val hasCompleted = if (totalCount > 0) "${completedCount}/${totalCount}" else "N/A (No active tasks)"

                val userLeaves = allPlannedLeaves.filter { it.userId == user.id && (targetDateStr.isEmpty() || it.endDate >= targetDateStr) }

                appendLine("EMPLOYEE: ${user.name} (ID: ${user.id})")
                appendLine("----------------------------------------------")
                appendLine("Task Status: $hasCompleted completed")
                
                if (activeTasksForUser.isNotEmpty()) {
                    appendLine("Active Tasks details:")
                    activeTasksForUser.forEach { task ->
                        val taskCompletion = completedMapByReminderId[task.id]
                        val taskIsDone = taskCompletion != null
                        val statusStr = if (taskIsDone) {
                            "✓ COMPLETED" + (if (taskCompletion?.payload != null) " (${taskCompletion.payload})" else "")
                        } else {
                            "✗ PENDING"
                        }
                        appendLine("  - [${task.frequency}] ${task.title}: $statusStr")
                    }
                } else {
                    appendLine("  (No assigned active tasks for this weekday)")
                }
                
                appendLine()
                appendLine("Planned Leaves:")
                if (userLeaves.isNotEmpty()) {
                    userLeaves.forEach { leave ->
                        appendLine("  - From ${leave.startDate} to ${leave.endDate} (${leave.status})")
                        appendLine("    Reason: ${leave.reason}")
                    }
                } else {
                    appendLine("  - No scheduled planned leaves of absence.")
                }
                appendLine("==============================================")
                appendLine()
            }
        }
    }

    fun generateCustomRangeReportText(startDate: LocalDate, endDate: LocalDate): String {
        return buildString {
            appendLine("==============================================")
            appendLine("      CORPORATE COMPLIANCE & METRICS REPORT")
            appendLine("               (MULTI-DAY RANGE)")
            appendLine("==============================================")
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
            appendLine("Date Range: ${startDate.format(formatter)} to ${endDate.format(formatter)}")
            appendLine("Generated At: 2026-05-24 (Local Sync)")
            appendLine("==============================================")
            appendLine()

            val employees = allUsers.filter { it.role != "ADMIN" }
            var currentLocalDate = startDate
            while (!currentLocalDate.isAfter(endDate)) {
                val targetDateStr = currentLocalDate.format(formatter)
                val targetDay = currentLocalDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
                val simulatedDayStr = viewModel.dateMapping.entries.firstOrNull { it.value == targetDateStr }?.key

                appendLine("----------------------------------------------")
                appendLine("DATE: $targetDateStr ($targetDay)")
                appendLine("----------------------------------------------")

                employees.forEach { user ->
                    val activeTasksForUser = allReminders.filter { reminder ->
                        val userMatches = reminder.targetUserId.isNullOrEmpty() || reminder.targetUserId.split(",").map { it.trim() }.contains(user.id)
                        if (!userMatches) return@filter false

                        val dayToQuery = simulatedDayStr ?: targetDay
                        viewModel.isReminderActiveOnDay(reminder, dayToQuery)
                    }

                    val completionsForTargetDate = allCompletions.filter { it.dateString == targetDateStr }
                    val completedUserTasks = completionsForTargetDate.filter { it.userId == user.id }
                    val completedMapByReminderId = completedUserTasks.associateBy { it.reminderId }

                    val completedCount = activeTasksForUser.count { completedMapByReminderId.containsKey(it.id) }
                    val totalCount = activeTasksForUser.size
                    val hasCompleted = if (totalCount > 0) "${completedCount}/${totalCount}" else "N/A (No active tasks)"

                    val userLeaves = allPlannedLeaves.filter { it.userId == user.id && targetDateStr >= it.startDate && targetDateStr <= it.endDate }

                    appendLine("  EMPLOYEE: ${user.name} (ID: ${user.id})")
                    appendLine("  Task Status: $hasCompleted completed")
                    
                    if (activeTasksForUser.isNotEmpty()) {
                        appendLine("  Active Tasks details:")
                        activeTasksForUser.forEach { task ->
                            val taskCompletion = completedMapByReminderId[task.id]
                            val taskIsDone = taskCompletion != null
                            val statusStr = if (taskIsDone) {
                                "✓ COMPLETED" + (if (taskCompletion?.payload != null) " (${taskCompletion.payload})" else "")
                            } else {
                                "✗ PENDING"
                            }
                            appendLine("    - [${task.frequency}] ${task.title}: $statusStr")
                        }
                    } else {
                        appendLine("    (No assigned active tasks for this weekday)")
                    }
                    
                    appendLine("  Planned Leaves:")
                    if (userLeaves.isNotEmpty()) {
                        userLeaves.forEach { leave ->
                            appendLine("    - From ${leave.startDate} to ${leave.endDate} (${leave.status})")
                            appendLine("      Reason: ${leave.reason}")
                        }
                    } else {
                        appendLine("    - No scheduled planned leaves of absence.")
                    }
                    appendLine("  - - - - - - - - - - - - - - - - - - - - - -")
                }
                appendLine()
                currentLocalDate = currentLocalDate.plusDays(1)
            }
            appendLine("==============================================")
        }
    }

    fun generateCustomPdfReportBytes(startDate: LocalDate, endDate: LocalDate): ByteArray {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        
        val titlePaint = android.graphics.Paint().apply {
            color = 0xFF1A237E.toInt()
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val subtitlePaint = android.graphics.Paint().apply {
            color = 0xFF3F51B5.toInt()
            textSize = 10.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val metaPaint = android.graphics.Paint().apply {
            color = 0xFF757575.toInt()
            textSize = 8f
            isAntiAlias = true
        }
        
        val textPaint = android.graphics.Paint().apply {
            color = 0xFF212121.toInt()
            textSize = 9f
            isAntiAlias = true
        }
        
        val boldTextPaint = android.graphics.Paint().apply {
            color = 0xFF212121.toInt()
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val headerBgPaint = android.graphics.Paint().apply {
            color = 0xFFE8EAF6.toInt()
            style = android.graphics.Paint.Style.FILL
        }
        
        val rowBgEvenPaint = android.graphics.Paint().apply {
            color = 0xFFF5F5F5.toInt()
            style = android.graphics.Paint.Style.FILL
        }

        val rowBgOddPaint = android.graphics.Paint().apply {
            color = 0xFFFFFFFF.toInt()
            style = android.graphics.Paint.Style.FILL
        }
        
        val gridPaint = android.graphics.Paint().apply {
            color = 0xFFC5CAE9.toInt()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        
        val successTextPaint = android.graphics.Paint().apply {
            color = 0xFF1B5E20.toInt()
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val pendingTextPaint = android.graphics.Paint().apply {
            color = 0xFFB71C1C.toInt()
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }

        var pageNumber = 1
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        
        var currentY = 45f
        
        fun checkNewPage() {
            if (currentY > 770f) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                
                currentY = 40f
                canvas.drawRect(30f, currentY, 565f, currentY + 3f, headerBgPaint)
                currentY += 15f
                canvas.drawText("CORPORATE COMPLIANCE REPORT (Page $pageNumber) - Dynamic Active Records", 30f, currentY, metaPaint)
                currentY += 15f
                canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
                currentY += 20f
            }
        }
        
        canvas.drawRect(30f, currentY, 565f, currentY + 40f, headerBgPaint)
        canvas.drawText("CORPORATE COMPLIANCE & METRICS STATEMENT", 40f, currentY + 25f, titlePaint)
        currentY += 55f
        
        val firstDate = startDate.format(formatter)
        val lastDate = endDate.format(formatter)
        val periodString = if (startDate == endDate) firstDate else "$firstDate to $lastDate"
        canvas.drawText("Analyzed Period: $periodString", 30f, currentY, subtitlePaint)
        currentY += 15f
        canvas.drawText("Generated: 2026-05-24 | Sync Code: SECURE_MD5 | Type: Material compliance report with tabular data", 30f, currentY, metaPaint)
        currentY += 15f
        canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
        currentY += 25f
        
        canvas.drawText("INDIVIDUAL COMPLIANCE SUMMARY MATRIX", 30f, currentY, subtitlePaint)
        currentY += 12f
        
        val tableStartY = currentY
        canvas.drawRect(30f, currentY, 565f, currentY + 18f, headerBgPaint)
        canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
        canvas.drawLine(30f, currentY + 18f, 565f, currentY + 18f, gridPaint)
        
        canvas.drawText("Employee Name", 35f, currentY + 12f, boldTextPaint)
        canvas.drawText("Corporate Role", 175f, currentY + 12f, boldTextPaint)
        canvas.drawText("Assigned Tasks", 280f, currentY + 12f, boldTextPaint)
        canvas.drawText("Completions", 380f, currentY + 12f, boldTextPaint)
        canvas.drawText("Compliance Rate", 470f, currentY + 12f, boldTextPaint)
        currentY += 18f
        
        val employees = allUsers.filter { it.role != "ADMIN" }
        employees.forEachIndexed { rowIdx, user ->
            var totalActiveTasksInRange = 0
            var totalCompletedTasksInRange = 0
            
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                val targetDateStr = curr.format(formatter)
                val targetDay = curr.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
                val simulatedDayStr = viewModel.dateMapping.entries.firstOrNull { it.value == targetDateStr }?.key
                
                val activeTasksForUser = allReminders.filter { reminder ->
                    val userMatches = reminder.targetUserId.isNullOrEmpty() || reminder.targetUserId.split(",").map { it.trim() }.contains(user.id)
                    if (!userMatches) return@filter false

                    val dayToQuery = simulatedDayStr ?: targetDay
                    viewModel.isReminderActiveOnDay(reminder, dayToQuery)
                }
                
                val completionsForTargetDate = allCompletions.filter { it.dateString == targetDateStr }
                val completedUserTasks = completionsForTargetDate.filter { it.userId == user.id }
                val completedMapByReminderId = completedUserTasks.associateBy { it.reminderId }
                
                totalActiveTasksInRange += activeTasksForUser.size
                totalCompletedTasksInRange += activeTasksForUser.count { completedMapByReminderId.containsKey(it.id) }
                
                curr = curr.plusDays(1)
            }
            
            val rowBg = if (rowIdx % 2 == 0) rowBgEvenPaint else rowBgOddPaint
            canvas.drawRect(30f, currentY, 565f, currentY + 18f, rowBg)
            
            canvas.drawText(user.name, 35f, currentY + 12f, textPaint)
            canvas.drawText(user.role, 175f, currentY + 12f, textPaint)
            canvas.drawText(totalActiveTasksInRange.toString(), 280f, currentY + 12f, textPaint)
            canvas.drawText(totalCompletedTasksInRange.toString(), 380f, currentY + 12f, textPaint)
            
            val percentage = if (totalActiveTasksInRange > 0) {
                (totalCompletedTasksInRange * 100) / totalActiveTasksInRange
            } else {
                100
            }
            val percentageColorPaint = if (percentage >= 80) successTextPaint else pendingTextPaint
            canvas.drawText("$percentage%", 470f, currentY + 12f, percentageColorPaint)
            
            canvas.drawLine(30f, currentY + 18f, 565f, currentY + 18f, gridPaint)
            currentY += 18f
            checkNewPage()
        }
        
        val tableEndY = currentY
        val colPositions = listOf(30f, 170f, 275f, 375f, 465f, 565f)
        colPositions.forEach { colX ->
            canvas.drawLine(colX, tableStartY, colX, tableEndY, gridPaint)
        }
        
        currentY += 25f
        checkNewPage()
        
        canvas.drawText("DETAILED PERFORMANCE LOG (DAILY TRACKING)", 30f, currentY, subtitlePaint)
        currentY += 12f
        canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
        currentY += 15f
        checkNewPage()
        
        var dDate = startDate
        while (!dDate.isAfter(endDate)) {
            val targetDateStr = dDate.format(formatter)
            val targetDay = dDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            val simulatedDayStr = viewModel.dateMapping.entries.firstOrNull { it.value == targetDateStr }?.key
            
            canvas.drawRect(30f, currentY, 565f, currentY + 15f, headerBgPaint)
            canvas.drawText("ACTUAL RECORD: $targetDateStr ($targetDay)", 35f, currentY + 11f, boldTextPaint)
            canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
            canvas.drawLine(30f, currentY + 15f, 565f, currentY + 15f, gridPaint)
            canvas.drawLine(30f, currentY, 30f, currentY + 15f, gridPaint)
            canvas.drawLine(565f, currentY, 565f, currentY + 15f, gridPaint)
            currentY += 18f
            checkNewPage()
            
            employees.forEach { user ->
                val activeTasksForUser = allReminders.filter { reminder ->
                    val userMatches = reminder.targetUserId.isNullOrEmpty() || reminder.targetUserId.split(",").map { it.trim() }.contains(user.id)
                    if (!userMatches) return@filter false

                    val dayToQuery = simulatedDayStr ?: targetDay
                    viewModel.isReminderActiveOnDay(reminder, dayToQuery)
                }
                
                val completionsForTargetDate = allCompletions.filter { it.dateString == targetDateStr }
                val completedUserTasks = completionsForTargetDate.filter { it.userId == user.id }
                val completedMapByReminderId = completedUserTasks.associateBy { it.reminderId }
                
                val userLeaves = allPlannedLeaves.filter { it.userId == user.id && targetDateStr >= it.startDate && targetDateStr <= it.endDate }
                
                canvas.drawText("Employee: ${user.name} (${user.role})", 40f, currentY + 10f, boldTextPaint)
                
                if (userLeaves.isNotEmpty()) {
                    val leaveLabel = "[ON ABSENCE: ${userLeaves.first().reason}]"
                    canvas.drawText(leaveLabel, 260f, currentY + 10f, pendingTextPaint)
                } else {
                    val comps = activeTasksForUser.count { completedMapByReminderId.containsKey(it.id) }
                    val tot = activeTasksForUser.size
                    canvas.drawText("Progress Rate: $comps/$tot Tasks Done", 260f, currentY + 10f, textPaint)
                }
                currentY += 15f
                checkNewPage()
                
                if (activeTasksForUser.isNotEmpty()) {
                    activeTasksForUser.forEach { task ->
                        val isDone = completedMapByReminderId.containsKey(task.id)
                        val statusText = if (isDone) "✓ COMPLETED" else "✗ PENDING"
                        val statusPaint = if (isDone) successTextPaint else pendingTextPaint
                        
                        canvas.drawText("  - [${task.frequency}] ${task.title}", 55f, currentY + 9f, textPaint)
                        canvas.drawText(statusText, 450f, currentY + 9f, statusPaint)
                        currentY += 14f
                        checkNewPage()
                    }
                } else {
                    canvas.drawText("  (No direct active tasks allocated)", 55f, currentY + 9f, metaPaint)
                    currentY += 14f
                    checkNewPage()
                }
                
                canvas.drawLine(40f, currentY - 3f, 540f, currentY - 3f, gridPaint)
                currentY += 6f
                checkNewPage()
            }
            
            currentY += 10f
            checkNewPage()
            dDate = dDate.plusDays(1)
        }
        
        pdfDocument.finishPage(currentPage)
        
        val outputStream = java.io.ByteArrayOutputStream()
        try {
            pdfDocument.writeTo(outputStream)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
        return outputStream.toByteArray()
    }

    fun generatePdfReportBytes(startIdx: Int, endIdx: Int): ByteArray {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val daysList = viewModel.daysOfWeek
        
        val titlePaint = android.graphics.Paint().apply {
            color = 0xFF1A237E.toInt()
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val subtitlePaint = android.graphics.Paint().apply {
            color = 0xFF3F51B5.toInt()
            textSize = 10.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val metaPaint = android.graphics.Paint().apply {
            color = 0xFF757575.toInt()
            textSize = 8f
            isAntiAlias = true
        }
        
        val textPaint = android.graphics.Paint().apply {
            color = 0xFF212121.toInt()
            textSize = 9f
            isAntiAlias = true
        }
        
        val boldTextPaint = android.graphics.Paint().apply {
            color = 0xFF212121.toInt()
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val headerBgPaint = android.graphics.Paint().apply {
            color = 0xFFE8EAF6.toInt()
            style = android.graphics.Paint.Style.FILL
        }
        
        val rowBgEvenPaint = android.graphics.Paint().apply {
            color = 0xFFF5F5F5.toInt()
            style = android.graphics.Paint.Style.FILL
        }

        val rowBgOddPaint = android.graphics.Paint().apply {
            color = 0xFFFFFFFF.toInt()
            style = android.graphics.Paint.Style.FILL
        }
        
        val gridPaint = android.graphics.Paint().apply {
            color = 0xFFC5CAE9.toInt()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        
        val successTextPaint = android.graphics.Paint().apply {
            color = 0xFF1B5E20.toInt()
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val pendingTextPaint = android.graphics.Paint().apply {
            color = 0xFFB71C1C.toInt()
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }

        var pageNumber = 1
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        
        var currentY = 45f
        
        fun checkNewPage() {
            if (currentY > 770f) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                
                currentY = 40f
                canvas.drawRect(30f, currentY, 565f, currentY + 3f, headerBgPaint)
                currentY += 15f
                canvas.drawText("CORPORATE COMPLIANCE REPORT (Page $pageNumber) - Dynamic Active Records", 30f, currentY, metaPaint)
                currentY += 15f
                canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
                currentY += 20f
            }
        }
        
        canvas.drawRect(30f, currentY, 565f, currentY + 40f, headerBgPaint)
        canvas.drawText("CORPORATE COMPLIANCE & METRICS STATEMENT", 40f, currentY + 25f, titlePaint)
        currentY += 55f
        
        val firstDay = daysList[startIdx]
        val lastDay = daysList[endIdx]
        val firstDate = viewModel.dateMapping[firstDay] ?: ""
        val lastDate = viewModel.dateMapping[lastDay] ?: ""
        
        val periodString = if (startIdx == endIdx) "$firstDate ($firstDay)" else "$firstDate ($firstDay) to $lastDate ($lastDay)"
        canvas.drawText("Analyzed Period: $periodString", 30f, currentY, subtitlePaint)
        currentY += 15f
        canvas.drawText("Generated: 2026-05-24 | Sync Code: SECURE_MD5 | Type: Material compliance report with tabular data", 30f, currentY, metaPaint)
        currentY += 15f
        canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
        currentY += 25f
        
        canvas.drawText("INDIVIDUAL COMPLIANCE SUMMARY MATRIX", 30f, currentY, subtitlePaint)
        currentY += 12f
        
        val tableStartY = currentY
        canvas.drawRect(30f, currentY, 565f, currentY + 18f, headerBgPaint)
        canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
        canvas.drawLine(30f, currentY + 18f, 565f, currentY + 18f, gridPaint)
        
        canvas.drawText("Employee Name", 35f, currentY + 12f, boldTextPaint)
        canvas.drawText("Corporate Role", 175f, currentY + 12f, boldTextPaint)
        canvas.drawText("Assigned Tasks", 280f, currentY + 12f, boldTextPaint)
        canvas.drawText("Completions", 380f, currentY + 12f, boldTextPaint)
        canvas.drawText("Compliance Rate", 470f, currentY + 12f, boldTextPaint)
        currentY += 18f
        
        val employees = allUsers.filter { it.role != "ADMIN" }
        employees.forEachIndexed { rowIdx, user ->
            var totalActiveTasksInRange = 0
            var totalCompletedTasksInRange = 0
            
            for (idx in startIdx..endIdx) {
                val targetDay = daysList[idx]
                val targetDateStr = viewModel.dateMapping[targetDay] ?: ""
                val activeTasksForUser = allReminders.filter { reminder ->
                    val userMatches = reminder.targetUserId.isNullOrEmpty() || reminder.targetUserId.split(",").map { it.trim() }.contains(user.id)
                    if (!userMatches) return@filter false

                    viewModel.isReminderActiveOnDay(reminder, targetDay)
                }
                
                val completionsForTargetDate = allCompletions.filter { it.dateString == targetDateStr }
                val completedUserTasks = completionsForTargetDate.filter { it.userId == user.id }
                val completedMapByReminderId = completedUserTasks.associateBy { it.reminderId }
                
                totalActiveTasksInRange += activeTasksForUser.size
                totalCompletedTasksInRange += activeTasksForUser.count { completedMapByReminderId.containsKey(it.id) }
            }
            
            val rowBg = if (rowIdx % 2 == 0) rowBgEvenPaint else rowBgOddPaint
            canvas.drawRect(30f, currentY, 565f, currentY + 18f, rowBg)
            
            canvas.drawText(user.name, 35f, currentY + 12f, textPaint)
            canvas.drawText(user.role, 175f, currentY + 12f, textPaint)
            canvas.drawText(totalActiveTasksInRange.toString(), 280f, currentY + 12f, textPaint)
            canvas.drawText(totalCompletedTasksInRange.toString(), 380f, currentY + 12f, textPaint)
            
            val percentage = if (totalActiveTasksInRange > 0) {
                (totalCompletedTasksInRange * 100) / totalActiveTasksInRange
            } else {
                100
            }
            val percentageColorPaint = if (percentage >= 80) successTextPaint else pendingTextPaint
            canvas.drawText("$percentage%", 470f, currentY + 12f, percentageColorPaint)
            
            canvas.drawLine(30f, currentY + 18f, 565f, currentY + 18f, gridPaint)
            currentY += 18f
            checkNewPage()
        }
        
        val tableEndY = currentY
        val colPositions = listOf(30f, 170f, 275f, 375f, 465f, 565f)
        colPositions.forEach { colX ->
            canvas.drawLine(colX, tableStartY, colX, tableEndY, gridPaint)
        }
        
        currentY += 25f
        checkNewPage()
        
        canvas.drawText("DETAILED PERFORMANCE LOG (DAILY TRACKING)", 30f, currentY, subtitlePaint)
        currentY += 12f
        canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
        currentY += 15f
        checkNewPage()
        
        for (idx in startIdx..endIdx) {
            val targetDay = daysList[idx]
            val targetDateStr = viewModel.dateMapping[targetDay] ?: ""
            
            canvas.drawRect(30f, currentY, 565f, currentY + 15f, headerBgPaint)
            canvas.drawText("ACTUAL RECORD: $targetDateStr ($targetDay)", 35f, currentY + 11f, boldTextPaint)
            canvas.drawLine(30f, currentY, 565f, currentY, gridPaint)
            canvas.drawLine(30f, currentY + 15f, 565f, currentY + 15f, gridPaint)
            canvas.drawLine(30f, currentY, 30f, currentY + 15f, gridPaint)
            canvas.drawLine(565f, currentY, 565f, currentY + 15f, gridPaint)
            currentY += 18f
            checkNewPage()
            
            employees.forEach { user ->
                val activeTasksForUser = allReminders.filter { reminder ->
                    val userMatches = reminder.targetUserId.isNullOrEmpty() || reminder.targetUserId.split(",").map { it.trim() }.contains(user.id)
                    if (!userMatches) return@filter false

                    viewModel.isReminderActiveOnDay(reminder, targetDay)
                }
                
                val completionsForTargetDate = allCompletions.filter { it.dateString == targetDateStr }
                val completedUserTasks = completionsForTargetDate.filter { it.userId == user.id }
                val completedMapByReminderId = completedUserTasks.associateBy { it.reminderId }
                
                val userLeaves = allPlannedLeaves.filter { it.userId == user.id && targetDateStr >= it.startDate && targetDateStr <= it.endDate }
                
                canvas.drawText("Employee: ${user.name} (${user.role})", 40f, currentY + 10f, boldTextPaint)
                
                if (userLeaves.isNotEmpty()) {
                    val leaveLabel = "[ON ABSENCE: ${userLeaves.first().reason}]"
                    canvas.drawText(leaveLabel, 260f, currentY + 10f, pendingTextPaint)
                } else {
                    val comps = activeTasksForUser.count { completedMapByReminderId.containsKey(it.id) }
                    val tot = activeTasksForUser.size
                    canvas.drawText("Progress Rate: $comps/$tot Tasks Done", 260f, currentY + 10f, textPaint)
                }
                currentY += 15f
                checkNewPage()
                
                if (activeTasksForUser.isNotEmpty()) {
                    activeTasksForUser.forEach { task ->
                        val isDone = completedMapByReminderId.containsKey(task.id)
                        val statusText = if (isDone) "✓ COMPLETED" else "✗ PENDING"
                        val statusPaint = if (isDone) successTextPaint else pendingTextPaint
                        
                        canvas.drawText("  - [${task.frequency}] ${task.title}", 55f, currentY + 9f, textPaint)
                        canvas.drawText(statusText, 450f, currentY + 9f, statusPaint)
                        currentY += 14f
                        checkNewPage()
                    }
                } else {
                    canvas.drawText("  (No direct active tasks allocated)", 55f, currentY + 9f, metaPaint)
                    currentY += 14f
                    checkNewPage()
                }
                
                canvas.drawLine(40f, currentY - 3f, 540f, currentY - 3f, gridPaint)
                currentY += 6f
                checkNewPage()
            }
            
            currentY += 10f
            checkNewPage()
        }
        
        pdfDocument.finishPage(currentPage)
        
        val outputStream = java.io.ByteArrayOutputStream()
        try {
            pdfDocument.writeTo(outputStream)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
        return outputStream.toByteArray()
    }

    var onBehalfCompleteTarget by remember { mutableStateOf<Pair<com.example.data.Reminder, com.example.data.User>?>(null) }
    var onBehalfUndoTarget by remember { mutableStateOf<Pair<com.example.data.Reminder, com.example.data.User>?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    var pdfBytesState by remember { mutableStateOf<ByteArray?>(null) }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    pdfBytesState?.let { bytes ->
                        outputStream.write(bytes)
                    }
                }
                android.widget.Toast.makeText(context, "PDF Compliance Report saved successfully!", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to save PDF: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(reportTextState.toByteArray(Charsets.UTF_8))
                }
                android.widget.Toast.makeText(context, "Report saved successfully!", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to save report: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedDay) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            val days = viewModel.daysOfWeek
                            val currentIndex = days.indexOf(selectedDay).coerceAtLeast(0)
                            if (totalDrag < -100f) {
                                // Swiped Left -> Go to next day
                                if (currentIndex < days.lastIndex) {
                                    viewModel.setSimulatedDay(days[currentIndex + 1])
                                }
                            } else if (totalDrag > 100f) {
                                // Swiped Right -> Go to previous day
                                if (currentIndex > 0) {
                                    viewModel.setSimulatedDay(days[currentIndex - 1])
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        }
                    )
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            ScreenHeader(
                title = "Live Compliance Overview",
                subtitle = "This matrix shows compliance logs of all corporate employee requirements on this calendar date.",
                icon = Icons.Default.Group,
                extraContent = {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            val isToday = selectedDay == viewModel.todayName
                            val adminDisplayDay = getDisplayDayName(selectedDay, viewModel.todayName, viewModel.daysOfWeek)
                            Text(
                                text = if (isToday) "Live Date: $dateStr ($adminDisplayDay)" else "Selected Date: $dateStr ($adminDisplayDay)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TEAM MEMBER COMPLIANCE HISTORY",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        showDownloadDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp).testTag("download_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Report Icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Download Report",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                }
            }

        // Loop over each employee to show their metrics
        items(allUsers.filter { it.role != "ADMIN" }) { user ->
            // Filter down reminders that are active specifically on this weekday for this worker
            val activeTasksForUser = allReminders.filter { reminder ->
                val userMatches = reminder.targetUserId.isNullOrEmpty() || reminder.targetUserId.split(",").map { it.trim() }.contains(user.id)
                if (!userMatches) return@filter false

                viewModel.isReminderActiveOnDay(reminder, selectedDay)
            }

            // Completed tasks of activeTasks for this user on this date
            val completedUserTasks = completionsForSelectedDate.filter { it.userId == user.id }
            val completedMapByReminderId = completedUserTasks.associateBy { it.reminderId }

            val completedCount = activeTasksForUser.count { completedMapByReminderId.containsKey(it.id) }
            val totalCount = activeTasksForUser.size
            val hasCompletedAll = totalCount > 0 && completedCount == totalCount

            // Get planned leaves for this specific user
            val userLeaves = allPlannedLeaves.filter { it.userId == user.id && (dateStr.isEmpty() || it.endDate >= dateStr) }

            val isDark = isSystemInDarkTheme()
            val cardBg = if (hasCompletedAll) {
                if (isDark) Color(0xFF0A2610) else Color(0xFFEEF9F2)
            } else {
                MaterialTheme.colorScheme.surface
            }
            val cardBorder = if (hasCompletedAll) {
                androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    if (isDark) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFF81C784).copy(alpha = 0.6f)
                )
            } else null

            val textColorTitle = if (hasCompletedAll) {
                if (isDark) Color(0xFFE8F5E9) else Color(0xFF1B4D22)
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            val textColorSubtitle = if (hasCompletedAll) {
                if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            val badgeContainerBg = if (hasCompletedAll) {
                if (isDark) Color(0xFF2E7D32) else Color(0xFFC3E6CB)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }

            val badgeTextCol = if (hasCompletedAll) {
                if (isDark) Color.White else Color(0xFF155724)
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compliance_row_${user.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = cardBg
                ),
                border = cardBorder,
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Row with Persona Details & Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColorTitle
                            )
                            Text(
                                text = "${user.role} • Task status",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColorSubtitle
                            )
                        }

                        // Complete / Target indicator
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = badgeContainerBg
                            )
                        ) {
                            Text(
                                text = "$completedCount / $totalCount Done",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextCol,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Duties and logs
                    if (activeTasksForUser.isEmpty()) {
                        Text(
                            text = "No assigned reminders active for this weekday.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeTasksForUser.forEach { task ->
                                val taskCompletion = completedMapByReminderId[task.id]
                                val taskIsDone = taskCompletion != null

                                val taskRowBg = if (taskIsDone) {
                                    if (isDark) Color(0xFF1E4622) else Color(0xFFDCEFE1)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                                val iconTint = if (taskIsDone) {
                                    if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                                val taskTextCol = if (hasCompletedAll) {
                                    if (isDark) Color(0xFFE8F5E9) else Color(0xFF1B4D22)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                val taskLabelCol = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = taskRowBg,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (taskIsDone) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = taskTextCol,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (taskIsDone && taskCompletion?.payload != null) {
                                            Text(
                                                text = taskCompletion.payload ?: "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = taskLabelCol,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }

                                        if (!taskIsDone) {
                                            IconButton(
                                                onClick = {
                                                    onBehalfCompleteTarget = Pair(task, user)
                                                },
                                                enabled = !isPastDisabled,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .testTag("complete_on_behalf_${user.id}_${task.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Complete as Admin/Lead",
                                                    tint = if (!isPastDisabled) (if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)) else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    onBehalfUndoTarget = Pair(task, user)
                                                },
                                                enabled = !isPastDisabled,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .testTag("undo_on_behalf_${user.id}_${task.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Undo Completion",
                                                    tint = if (!isPastDisabled) MaterialTheme.colorScheme.error else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Embed Planned Leaves list inside employee card
                    if (userLeaves.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val dividerCol = if (hasCompletedAll) {
                            if (isDark) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color(0xFF81C784).copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }

                        val plannedLeaveHeaderCol = if (hasCompletedAll) {
                            if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }

                        HorizontalDivider(
                            color = dividerCol,
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🗓️ Planned Leaves:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = plannedLeaveHeaderCol,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            userLeaves.forEach { leave ->
                                val leaveRowBg = if (hasCompletedAll) {
                                    if (isDark) Color(0xFF153319) else Color(0xFFEAF5EC)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                }

                                val leaveDateCol = if (hasCompletedAll) {
                                    if (isDark) Color.White else Color(0xFF1B4D22)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }

                                val leaveReasonCol = if (hasCompletedAll) {
                                    if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                val leaveStatusContainerBg = if (hasCompletedAll) {
                                    if (isDark) Color(0xFF2E7D32).copy(alpha = 0.5f) else Color(0xFFC3E6CB)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }

                                val leaveStatusTextCol = if (hasCompletedAll) {
                                    if (isDark) Color(0xFFE8F5E9) else Color(0xFF155724)
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = leaveRowBg,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${leave.startDate} to ${leave.endDate}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = leaveDateCol
                                        )
                                        Text(
                                            text = "Reason: ${leave.reason}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = leaveReasonCol
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = leaveStatusContainerBg,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = leave.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = leaveStatusTextCol
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (onBehalfCompleteTarget != null) {
        val (task, employee) = onBehalfCompleteTarget!!
        AlertDialog(
            onDismissRequest = { onBehalfCompleteTarget = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(text = "Confirm Completion On Behalf") },
            text = { Text(text = "Are you sure you want to mark the task \"${task.title}\" as Completed on behalf of ${employee.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completeTaskOnBehalf(task.id, employee.id)
                        onBehalfCompleteTarget = null
                    },
                    modifier = Modifier.testTag("confirm_complete_on_behalf_btn")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onBehalfCompleteTarget = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (onBehalfUndoTarget != null) {
        val (task, employee) = onBehalfUndoTarget!!
        AlertDialog(
            onDismissRequest = { onBehalfUndoTarget = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(text = "Confirm Marking as Incomplete") },
            text = { Text(text = "Are you sure you want to mark the task \"${task.title}\" as Incomplete (remove completion) on behalf of ${employee.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeCompletionOnBehalf(task.id, employee.id)
                        onBehalfUndoTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_undo_on_behalf_btn")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onBehalfUndoTarget = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDownloadDialog) {
        val todayName = viewModel.todayName
        val todayDate = viewModel.dateMapping[todayName] ?: ""
        val daysList = viewModel.daysOfWeek
        
        val todayIdx = daysList.indexOf(todayName).coerceAtLeast(0)
        val yesterdayIdx = if (todayIdx > 0) todayIdx - 1 else 13
        val yesterdayName = daysList[yesterdayIdx]
        val yesterdayDateStr = viewModel.dateMapping[yesterdayName] ?: ""

        var selectedPeriod by remember { mutableStateOf("Today") }

        val context = androidx.compose.ui.platform.LocalContext.current
        val showReportDatePicker = { isStartDate: Boolean ->
            val currentDate = if (isStartDate) customStartDate else customEndDate
            val y = currentDate.year
            val m = currentDate.monthValue - 1
            val d = currentDate.dayOfMonth

            val picker = android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val selected = LocalDate.of(year, month + 1, dayOfMonth)
                    if (isStartDate) {
                        customStartDate = selected
                    } else {
                        customEndDate = selected
                    }
                },
                y, m, d
            )
            try {
                val maxInstant = todayLocalDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                val minInstant = todayLocalDate.minusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                picker.datePicker.maxDate = maxInstant.toEpochMilli()
                picker.datePicker.minDate = minInstant.toEpochMilli()
            } catch (e: Exception) {
                // ignore
            }
            picker.show()
        }

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            icon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Download Compliance Report") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Select your target period coverage for compliance metrics:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Unified Chip Selector Row (Custom visual chips)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val periods = listOf(
                            Triple("Today", "Today", "📅"),
                            Triple("Yesterday", "Yesterday", "🗓️"),
                            Triple("Custom", "Custom", "🌟")
                        )
                        periods.forEach { (key, label, emoji) ->
                            val isSelected = selectedPeriod == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .clickable { selectedPeriod = key }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = emoji,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Content details based on selection
                    when (selectedPeriod) {
                        "Today" -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "📅 Coverage: Current Day",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "$todayName ($todayDate)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Generates a full-day compliance status report with active teammates and their physical presence confirmations.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        "Yesterday" -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.12f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "🗓️ Coverage: Previous Day",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "$yesterdayName ($yesterdayDateStr)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Generates the historic compliance record from the previous business day.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        "Custom" -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "🌟 Coverage: Custom Range",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Select a start and end calendar date range (last 1 month) to aggregate compliance analytics.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // Clickable Start Date field
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showReportDatePicker(true) }
                                    ) {
                                        OutlinedTextField(
                                            value = customStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)),
                                            onValueChange = {},
                                            readOnly = true,
                                            enabled = false,
                                            label = { Text("Start Date") },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Event,
                                                    contentDescription = "Select Start Date"
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }

                                    // Clickable End Date field
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showReportDatePicker(false) }
                                    ) {
                                        OutlinedTextField(
                                            value = customEndDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)),
                                            onValueChange = {},
                                            readOnly = true,
                                            enabled = false,
                                            label = { Text("End Date") },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Event,
                                                    contentDescription = "Select End Date"
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }

                                    val isRangeValid = !customStartDate.isAfter(customEndDate)
                                    if (!isRangeValid) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Invalid window: End Date must be after or equal to Start Date.",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Unified single action button
                    val isRangeValid = !customStartDate.isAfter(customEndDate)
                    val isDownloadEnabled = selectedPeriod != "Custom" || isRangeValid

                    Button(
                        onClick = {
                            when (selectedPeriod) {
                                "Today" -> {
                                    val todayIdx = daysList.indexOf(todayName).coerceAtLeast(0)
                                    pdfBytesState = generatePdfReportBytes(todayIdx, todayIdx)
                                    createPdfLauncher.launch("Employee_Compliance_Report_Today_${todayDate}.pdf")
                                }
                                "Yesterday" -> {
                                    val yesterdayIdx = daysList.indexOf(yesterdayName).coerceAtLeast(0)
                                    pdfBytesState = generatePdfReportBytes(yesterdayIdx, yesterdayIdx)
                                    createPdfLauncher.launch("Employee_Compliance_Report_Yesterday_${yesterdayDateStr}.pdf")
                                }
                                "Custom" -> {
                                    val startFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
                                    val startDtStr = customStartDate.format(startFormatter)
                                    val endDtStr = customEndDate.format(startFormatter)
                                    pdfBytesState = generateCustomPdfReportBytes(customStartDate, customEndDate)
                                    createPdfLauncher.launch("Employee_Compliance_Report_Range_${startDtStr}_to_${endDtStr}.pdf")
                                }
                            }
                            showDownloadDialog = false
                        },
                        enabled = isDownloadEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (selectedPeriod) {
                                "Today" -> MaterialTheme.colorScheme.primary
                                "Yesterday" -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedPeriod) {
                                "Today" -> "Download Today's PDF"
                                "Yesterday" -> "Download Yesterday's PDF"
                                else -> "Download Custom Range PDF"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showDownloadDialog = false }
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

    }
}

@Composable
fun SimulationCalendarPicker(
    selectedDateStr: String,
    onDateSelected: (String) -> Unit,
    dateMapping: Map<String, String>,
    daysOfWeek: List<String>,
    todayName: String
) {
    val todayIndex = daysOfWeek.indexOf(todayName).coerceAtLeast(0)
    val daysHeader = listOf("M", "T", "W", "T", "F", "S", "S")
    
    val week1 = daysOfWeek.take(7)
    val week2 = daysOfWeek.drop(7).take(7)
    
    val firstDateStr = dateMapping[daysOfWeek.firstOrNull()] ?: ""
    val lastDateStr = dateMapping[daysOfWeek.lastOrNull()] ?: ""
    val headerText = remember(firstDateStr, lastDateStr) {
        try {
            val parser = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
            val monFmt = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.US)
            val d1 = java.time.LocalDate.parse(firstDateStr, parser)
            val d2 = java.time.LocalDate.parse(lastDateStr, parser)
            if (d1.month == d2.month) {
                d1.format(monFmt)
            } else {
                val shortMon = java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.US)
                "${d1.format(shortMon)} - ${d2.format(shortMon)} ${d2.year}"
            }
        } catch (e: Exception) {
            "Simulation Calendar"
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = "2-Week Simulator View",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysHeader.forEach { dayAbbrev ->
                    Text(
                        text = dayAbbrev,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Week 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                week1.forEach { dayName ->
                    val dateVal = dateMapping[dayName] ?: ""
                    val isToday = dayName == todayName
                    val isPast = daysOfWeek.indexOf(dayName) < todayIndex
                    val isSelected = selectedDateStr == dateVal
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CalendarDayCell(
                            dayName = dayName,
                            dateStr = dateVal,
                            isSelected = isSelected,
                            isToday = isToday,
                            isPast = isPast,
                            onDateSelected = onDateSelected
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Week 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                week2.forEach { dayName ->
                    val dateVal = dateMapping[dayName] ?: ""
                    val isToday = dayName == todayName
                    val isPast = daysOfWeek.indexOf(dayName) < todayIndex
                    val isSelected = selectedDateStr == dateVal
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CalendarDayCell(
                            dayName = dayName,
                            dateStr = dateVal,
                            isSelected = isSelected,
                            isToday = isToday,
                            isPast = isPast,
                            onDateSelected = onDateSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    dayName: String,
    dateStr: String,
    isSelected: Boolean,
    isToday: Boolean,
    isPast: Boolean,
    onDateSelected: (String) -> Unit
) {
    val dayOfMonth = remember(dateStr) {
        try {
            val d = java.time.LocalDate.parse(dateStr)
            d.dayOfMonth.toString()
        } catch (e: Exception) {
            if (dateStr.length >= 10) dateStr.substring(8).toIntOrNull()?.toString() ?: "" else ""
        }
    }

    val interactionModifier = if (isPast) {
        Modifier
    } else {
        Modifier.clickable { onDateSelected(dateStr) }
    }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val borderStroke = when {
        isToday && !isSelected -> androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(backgroundColor)
            .then(if (borderStroke != null) Modifier.border(borderStroke, CircleShape) else Modifier)
            .then(interactionModifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayOfMonth,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}






