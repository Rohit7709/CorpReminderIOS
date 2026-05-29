package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.User

@Composable
fun MPinInputFields(
    pin: String,
    onPinChange: (String) -> Unit,
    pinLength: Int = 6,
    obscureText: Boolean = true,
    testTagPrefix: String = "mpin_"
) {
    val focusRequester = remember { FocusRequester() }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Render 6 elegant, undistorted square boxes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until pinLength) {
                val isFocused = i == pin.length
                val char = pin.getOrNull(i)
                val hasValue = char != null
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else if (hasValue) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { focusRequester.requestFocus() }
                        .testTag("$testTagPrefix$i"),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasValue) {
                        if (obscureText) {
                            // Perfect round bullet that never gets distorted
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface)
                            )
                        } else {
                            Text(
                                text = char.toString(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    } else if (isFocused) {
                        // Display active indicator cursor line
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(18.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        
        // This is the actual functional text field covering the field, masked so only custom visual boxes show
        BasicTextField(
            value = pin,
            onValueChange = { newValue ->
                val filtered = newValue.filter { it.isDigit() }
                if (filtered.length <= pinLength) {
                    onPinChange(filtered)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester)
                .alpha(0.01f),
            textStyle = TextStyle(color = Color.Transparent)
        )
    }
}

val PresetSecurityQuestions = listOf(
    "What is your mother's maiden name?",
    "What was the name of your first pet?",
    "What was the name of your elementary school?",
    "In what city were you born?",
    "What was your childhood nickname?",
    "What is your favorite food?"
)

enum class LoginStep {
    ENTER_ID,
    CREATE_PASSWORD,
    SETUP_SECURITY_QUESTIONS,
    ENTER_PASSWORD,
    VERIFY_SECURITY_QUESTIONS,
    RESET_MPIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginRegisterScreen(viewModel: ReminderViewModel, modifier: Modifier = Modifier) {
    var userIdInput by remember { mutableStateOf(viewModel.lastUserId) }
    var passwordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }

    // Security questions configuration states
    var q1Selection by remember { mutableStateOf("") }
    var a1Input by remember { mutableStateOf("") }
    var q2Selection by remember { mutableStateOf("") }
    var a2Input by remember { mutableStateOf("") }
    var q3Selection by remember { mutableStateOf("") }
    var a3Input by remember { mutableStateOf("") }

    // Recovery answers verification states
    var recoveryAnswer1 by remember { mutableStateOf("") }
    var recoveryAnswer2 by remember { mutableStateOf("") }
    var recoveryAnswer3 by remember { mutableStateOf("") }
    var selectedRandomQuestionSlot by remember { mutableStateOf<Int?>(null) }

    // Reset MPIN states
    var resetMpinInput by remember { mutableStateOf("") }
    var resetConfirmMpinInput by remember { mutableStateOf("") }

    var activePickingQuestionSlot by remember { mutableStateOf<Int?>(null) } // 1, 2, or 3

    var loginStep by remember { mutableStateOf(LoginStep.ENTER_ID) }
    var targetUser by remember { mutableStateOf<User?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val sessionTimeoutLoggedOut by viewModel.sessionTimeoutLoggedOut.collectAsStateWithLifecycle()
    LaunchedEffect(sessionTimeoutLoggedOut) {
        if (sessionTimeoutLoggedOut) {
            errorMsg = "Current session was timed out due to inactivity."
            viewModel.clearSessionTimeoutLoggedOut()
        }
    }

    // Dialog for picking questions
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
                        // Disable if already chosen in another slot
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
                                    color = if (isAlreadyChosen) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (isAlreadyChosen) {
                                    Text(
                                        text = "Chosen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 56.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Corporate Brand Header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CorporateFare,
                    contentDescription = "Corporate Logo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "CorpRemind HQ",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Text(
                text = "Secure Desk Operations Portal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Simplistic Premium Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = when (loginStep) {
                            LoginStep.ENTER_ID -> "Sign In"
                            LoginStep.CREATE_PASSWORD -> "Create 6-Digit MPin"
                            LoginStep.SETUP_SECURITY_QUESTIONS -> "Configure Security Questions"
                            LoginStep.ENTER_PASSWORD -> "Verify 6-Digit MPin"
                            LoginStep.VERIFY_SECURITY_QUESTIONS -> "Identify Verification"
                            LoginStep.RESET_MPIN -> "Reset MPin"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Error Box
                    if (errorMsg != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = errorMsg ?: "",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = loginStep,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "login_step_transition"
                    ) { step ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            when (step) {
                                LoginStep.ENTER_ID -> {
                                    Text(
                                        text = "Please enter your Corporate User ID to proceed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedTextField(
                                        value = userIdInput,
                                        onValueChange = {
                                            userIdInput = it
                                            errorMsg = null
                                        },
                                        label = { Text("Corporate User ID") },
                                        placeholder = { Text("e.g., employee_id") },
                                        leadingIcon = { 
                                            Icon(
                                                imageVector = Icons.Default.Badge, 
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            ) 
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("login_email_input")
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            if (userIdInput.isBlank()) {
                                                errorMsg = "Please enter your Corporate User ID."
                                            } else {
                                                val user = viewModel.getUserForLogin(userIdInput)
                                                if (user == null) {
                                                    errorMsg = "User ID not found. Ask an Admin to create your account."
                                                } else {
                                                    targetUser = user
                                                    errorMsg = null
                                                    if (!user.passwordCreated) {
                                                        loginStep = LoginStep.CREATE_PASSWORD
                                                    } else {
                                                        loginStep = LoginStep.ENTER_PASSWORD
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("auth_submit_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Next",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                                LoginStep.CREATE_PASSWORD -> {
                                    val userName = targetUser?.name ?: ""
                                    Text(
                                        text = "Welcome to your first login, $userName! Please create a secure 6-digit MPin to activate your portal account.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "New 6-Digit MPin",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Box(modifier = Modifier.fillMaxWidth().testTag("login_new_password_input")) {
                                        MPinInputFields(
                                            pin = newPasswordInput,
                                            onPinChange = {
                                                newPasswordInput = it
                                                errorMsg = null
                                            },
                                            obscureText = !isPasswordVisible,
                                            testTagPrefix = "new_mpin_"
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Confirm 6-Digit MPin",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Box(modifier = Modifier.fillMaxWidth().testTag("login_confirm_password_input")) {
                                        MPinInputFields(
                                            pin = confirmPasswordInput,
                                            onPinChange = {
                                                confirmPasswordInput = it
                                                errorMsg = null
                                            },
                                            obscureText = !isPasswordVisible,
                                            testTagPrefix = "confirm_mpin_"
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle visibility",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isPasswordVisible) "Hide MPin" else "Show MPin",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            when {
                                                newPasswordInput.isBlank() -> {
                                                    errorMsg = "Please enter a 6-digit MPin."
                                                }
                                                newPasswordInput.length < 6 -> {
                                                    errorMsg = "MPin must be exactly 6 digits."
                                                }
                                                newPasswordInput != confirmPasswordInput -> {
                                                    errorMsg = "MPins do not match."
                                                }
                                                else -> {
                                                    errorMsg = null
                                                    loginStep = LoginStep.SETUP_SECURITY_QUESTIONS
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("auth_submit_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Next: Security Questions",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            loginStep = LoginStep.ENTER_ID
                                            errorMsg = null
                                            newPasswordInput = ""
                                            confirmPasswordInput = ""
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Back to corporate login ID")
                                    }
                                }
                                LoginStep.SETUP_SECURITY_QUESTIONS -> {
                                    Text(
                                        text = "Please establish three security questions to protect your corporate console and enable MPin self-recovery.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Slots Question 1
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Question 1", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                OutlinedButton(
                                                    onClick = { activePickingQuestionSlot = 1 },
                                                    modifier = Modifier.fillMaxWidth().testTag("q1_select_btn"),
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
                                                    onValueChange = { a1Input = it; errorMsg = null },
                                                    label = { Text("Secret Answer 1") },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth().testTag("a1_input_field")
                                                )
                                            }
                                        }

                                        // Slots Question 2
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Question 2", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                OutlinedButton(
                                                    onClick = { activePickingQuestionSlot = 2 },
                                                    modifier = Modifier.fillMaxWidth().testTag("q2_select_btn"),
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
                                                    onValueChange = { a2Input = it; errorMsg = null },
                                                    label = { Text("Secret Answer 2") },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth().testTag("a2_input_field")
                                                )
                                            }
                                        }

                                        // Slots Question 3
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Question 3", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                OutlinedButton(
                                                    onClick = { activePickingQuestionSlot = 3 },
                                                    modifier = Modifier.fillMaxWidth().testTag("q3_select_btn"),
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
                                                    onValueChange = { a3Input = it; errorMsg = null },
                                                    label = { Text("Secret Answer 3") },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth().testTag("a3_input_field")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Button(
                                            onClick = {
                                                when {
                                                    q1Selection.isBlank() || q2Selection.isBlank() || q3Selection.isBlank() -> {
                                                        errorMsg = "Please configure all 3 security questions."
                                                    }
                                                    a1Input.trim().isBlank() || a2Input.trim().isBlank() || a3Input.trim().isBlank() -> {
                                                        errorMsg = "Please supply secret answers for all questions."
                                                    }
                                                    q1Selection == q2Selection || q2Selection == q3Selection || q1Selection == q3Selection -> {
                                                        errorMsg = "All selected questions must be distinct."
                                                    }
                                                    else -> {
                                                        val u = targetUser
                                                        if (u != null) {
                                                            viewModel.createPasswordForUser(
                                                                u, newPasswordInput,
                                                                q1Selection, a1Input.trim(),
                                                                q2Selection, a2Input.trim(),
                                                                q3Selection, a3Input.trim()
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("submit_questions_btn"),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Set up & Enter Council", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        TextButton(onClick = { loginStep = LoginStep.CREATE_PASSWORD }) {
                                            Text("Back to password creation")
                                        }
                                    }
                                }
                                LoginStep.ENTER_PASSWORD -> {
                                    val userName = targetUser?.name ?: ""
                                    Text(
                                        text = "Portal identity configured. Enter the 6-digit MPin for $userName to login.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "6-Digit MPin",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Box(modifier = Modifier.fillMaxWidth().testTag("login_password_input")) {
                                        MPinInputFields(
                                            pin = passwordInput,
                                            onPinChange = {
                                                passwordInput = it
                                                errorMsg = null
                                            },
                                            obscureText = !isPasswordVisible,
                                            testTagPrefix = "verify_mpin_"
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val currentU = targetUser
                                                if (currentU != null) {
                                                    val freshUser = viewModel.getUserForLogin(currentU.id) ?: currentU
                                                    targetUser = freshUser
                                                    if (freshUser.question1 != null && freshUser.answer1 != null) {
                                                        loginStep = LoginStep.VERIFY_SECURITY_QUESTIONS
                                                        errorMsg = null
                                                        recoveryAnswer1 = ""
                                                        recoveryAnswer2 = ""
                                                        recoveryAnswer3 = ""
                                                    } else {
                                                        errorMsg = "No security questions have been set for this account. Please request an Admin to reset your MPin."
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("forgot_mpin_btn")
                                        ) {
                                            Text("Forgot MPin?", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.error))
                                        }

                                        TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle visibility",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isPasswordVisible) "Hide MPin" else "Show MPin",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            if (passwordInput.isBlank()) {
                                                errorMsg = "Please enter your 6-digit MPin."
                                            } else if (passwordInput.length < 6) {
                                                errorMsg = "MPin must be exactly 6 digits."
                                            } else {
                                                val u = targetUser
                                                if (u != null) {
                                                    val authenticated = viewModel.verifyAndLogin(u, passwordInput)
                                                    if (!authenticated) {
                                                        errorMsg = "Incorrect MPin. Please try again."
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("auth_submit_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Verify & Access Console",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            loginStep = LoginStep.ENTER_ID
                                            errorMsg = null
                                            passwordInput = ""
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Switch Corporate Account")
                                    }
                                }
                                LoginStep.VERIFY_SECURITY_QUESTIONS -> {
                                    val freshUserVal = targetUser?.let { viewModel.getUserForLogin(it.id) } ?: targetUser
                                    val u = freshUserVal
                                    if (selectedRandomQuestionSlot == null && u != null) {
                                        val availableSlots = mutableListOf<Int>()
                                        if (!u.question1.isNullOrBlank()) availableSlots.add(1)
                                        if (!u.question2.isNullOrBlank()) availableSlots.add(2)
                                        if (!u.question3.isNullOrBlank()) availableSlots.add(3)
                                        if (availableSlots.isNotEmpty()) {
                                            selectedRandomQuestionSlot = availableSlots.random()
                                        }
                                    }
                                    Text(
                                        text = "Please answer the security question for User ID '${u?.id}' to change your MPin.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (selectedRandomQuestionSlot == 1 && u?.question1 != null) {
                                            OutlinedTextField(
                                                value = recoveryAnswer1,
                                                onValueChange = { recoveryAnswer1 = it; errorMsg = null },
                                                label = { Text(u.question1) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().testTag("recovery_a1_field")
                                            )
                                        }
                                        if (selectedRandomQuestionSlot == 2 && u?.question2 != null) {
                                            OutlinedTextField(
                                                value = recoveryAnswer2,
                                                onValueChange = { recoveryAnswer2 = it; errorMsg = null },
                                                label = { Text(u.question2) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().testTag("recovery_a2_field")
                                            )
                                        }
                                        if (selectedRandomQuestionSlot == 3 && u?.question3 != null) {
                                            OutlinedTextField(
                                                value = recoveryAnswer3,
                                                onValueChange = { recoveryAnswer3 = it; errorMsg = null },
                                                label = { Text(u.question3) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().testTag("recovery_a3_field")
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Button(
                                            onClick = {
                                                val verifiedSlot1 = if (u?.question1 != null) {
                                                    recoveryAnswer1.trim().equals(u.answer1?.trim(), ignoreCase = true)
                                                } else true

                                                val verifiedSlot2 = if (u?.question2 != null) {
                                                    recoveryAnswer2.trim().equals(u.answer2?.trim(), ignoreCase = true)
                                                } else true

                                                val verifiedSlot3 = if (u?.question3 != null) {
                                                    recoveryAnswer3.trim().equals(u.answer3?.trim(), ignoreCase = true)
                                                } else true

                                                val has1_v = selectedRandomQuestionSlot == 1 && recoveryAnswer1.trim().isNotEmpty()
                                                val has2_v = selectedRandomQuestionSlot == 2 && recoveryAnswer2.trim().isNotEmpty()
                                                val has3_v = selectedRandomQuestionSlot == 3 && recoveryAnswer3.trim().isNotEmpty()
                                                val matches1_v = if (has1_v) recoveryAnswer1.trim().equals(u?.answer1?.trim(), ignoreCase = true) else false
                                                val matches2_v = if (has2_v) recoveryAnswer2.trim().equals(u?.answer2?.trim(), ignoreCase = true) else false
                                                val matches3_v = if (has3_v) recoveryAnswer3.trim().equals(u?.answer3?.trim(), ignoreCase = true) else false
                                                val matchCount_v = (if (matches1_v) 1 else 0) + (if (matches2_v) 1 else 0) + (if (matches3_v) 1 else 0)
                                                val wrongCount_v = (if (has1_v && !matches1_v) 1 else 0) + (if (has2_v && !matches2_v) 1 else 0) + (if (has3_v && !matches3_v) 1 else 0)
                                                if (matchCount_v >= 1 && wrongCount_v == 0) {
                                                    errorMsg = null
                                                    loginStep = LoginStep.RESET_MPIN
                                                    resetMpinInput = ""
                                                    resetConfirmMpinInput = ""
                                                } else {
                                                    errorMsg = "Incorrect answer. Please verify and try again."
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("verify_questions_btn"),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Verify Security Answer", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        }

                                        TextButton(
                                            onClick = {
                                                loginStep = LoginStep.ENTER_PASSWORD
                                                errorMsg = null
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Cancel Recovery")
                                        }
                                    }
                                }
                                LoginStep.RESET_MPIN -> {
                                    Text(
                                        text = "Answers verified successfully. Please enter your new 6-digit MPin below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "New 6-Digit MPin",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Box(modifier = Modifier.fillMaxWidth().testTag("reset_mpin_new_input")) {
                                        MPinInputFields(
                                            pin = resetMpinInput,
                                            onPinChange = {
                                                resetMpinInput = it
                                                errorMsg = null
                                            },
                                            obscureText = !isPasswordVisible,
                                            testTagPrefix = "reset_new_mpin_"
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Confirm 6-Digit MPin",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Box(modifier = Modifier.fillMaxWidth().testTag("reset_mpin_confirm_input")) {
                                        MPinInputFields(
                                            pin = resetConfirmMpinInput,
                                            onPinChange = {
                                                resetConfirmMpinInput = it
                                                errorMsg = null
                                            },
                                            obscureText = !isPasswordVisible,
                                            testTagPrefix = "reset_confirm_mpin_"
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle visibility",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isPasswordVisible) "Hide MPin" else "Show MPin",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            when {
                                                resetMpinInput.isBlank() -> {
                                                    errorMsg = "Please enter a 6-digit MPin."
                                                }
                                                resetMpinInput.length < 6 -> {
                                                    errorMsg = "MPin must be exactly 6 digits."
                                                }
                                                resetMpinInput != resetConfirmMpinInput -> {
                                                    errorMsg = "MPins do not match."
                                                }
                                                else -> {
                                                    val u = targetUser
                                                    if (u != null) {
                                                        viewModel.resetMPinForUser(u.id, resetMpinInput)
                                                        val updatedUser = u.copy(password = resetMpinInput, passwordCreated = true)
                                                        viewModel.verifyAndLogin(updatedUser, resetMpinInput)
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("reset_mpin_submit_btn"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Reset MPin & Enter Console",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            loginStep = LoginStep.ENTER_PASSWORD
                                            errorMsg = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cancel Reset")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Copyright © 2026 Developed by Murtaza • v${com.example.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
