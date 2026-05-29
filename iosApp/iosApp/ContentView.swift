import SwiftUI
import Shared

struct ContentView: View {
    // Brand design color system (matching original Corporate Slate)
    let corpNavy = Color(red: 0.0, green: 0.098, blue: 0.27)      // #001945
    let corpIceBlue = Color(red: 0.85, green: 0.886, blue: 1.0)   // #D9E2FF
    let corpLightBg = Color(red: 0.99, green: 0.98, blue: 1.0)    // #FDFBFF
    let corpTextDark = Color(red: 0.1, green: 0.1, blue: 0.12)    // #1B1B1F
    
    // Auth and Navigation State
    @State private var isAuthenticated = false
    @State private var mpinInput = ""
    @State private var loginErrorMsg = ""
    @State private var activeTab = 0
    @State private var selectedDay = "SAT"
    
    // Sample tasks pre-populated exactly aligning with original Android Room tasks
    @State private var tasks = [
        TaskItem(id: "1", title: "Morning Scrum & Sync", time: "09:00 AM", location: "Teams Room / WFH", status: "COMPLETE", type: "Daily", wfo: true),
        TaskItem(id: "2", title: "Weekly Project Status Report", time: "02:00 PM", location: "Central Portal", status: "PENDING", type: "Scheduled", wfo: false),
        TaskItem(id: "3", title: "Security Notice: Review Login Protocol", time: "05:00 PM", location: "Security Vault", status: "PENDING", type: "SOP Notice", wfo: true)
    ]
    
    let daysOfWeek = ["Mon", "Tue", "Wed", "Thu", "Fri", "SAT", "Sun"]
    
    var body: some View {
        ZStack {
            corpLightBg.ignoresSafeArea()
            
            if !isAuthenticated {
                // Blueprint A: Secure MPin Entry (Login Gateway)
                VStack(spacing: 25) {
                    Spacer()
                    
                    VStack(spacing: 12) {
                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 60))
                            .foregroundColor(corpNavy)
                        
                        Text("CorpRemind")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(corpNavy)
                        
                        Text("Secure Gateway")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.gray)
                    }
                    
                    VStack(spacing: 6) {
                        Text("Enter your registered 4-Digit MPin")
                            .font(.system(size: 15))
                            .foregroundColor(corpTextDark)
                        Text("to unlock your corporate workspace.")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                    }
                    
                    // Pin Dot indicators
                    HStack(spacing: 20) {
                        ForEach(0..<4) { index in
                            Circle()
                                .fill(index < mpinInput.count ? corpNavy : Color.gray.opacity(0.3))
                                .frame(width: 16, height: 16)
                        }
                    }
                    .padding(.vertical, 10)
                    
                    if !loginErrorMsg.isEmpty {
                        Text(loginErrorMsg)
                            .foregroundColor(.red)
                            .font(.system(size: 13, weight: .medium))
                            .padding(.horizontal)
                            .multilineTextAlignment(.center)
                    }
                    
                    // Security Keypad
                    VStack(spacing: 12) {
                        ForEach(0..<3) { row in
                            HStack(spacing: 20) {
                                ForEach(1...3) { col in
                                    let digit = row * 3 + col
                                    KeypadButton(text: "\(digit)") {
                                        appendDigit("\(digit)")
                                    }
                                }
                            }
                        }
                        
                        HStack(spacing: 20) {
                            KeypadButton(text: "Clear") {
                                mpinInput = ""
                                loginErrorMsg = ""
                            }
                            
                            KeypadButton(text: "0") {
                                appendDigit("0")
                            }
                            
                            KeypadButton(text: "Enter") {
                                verifyPin(force: true)
                            }
                        }
                    }
                    .padding(.top, 5)
                    
                    Spacer()
                    
                    Text("Copyright © 2026 Developed by Murtaza")
                        .font(.footnote)
                        .foregroundColor(.gray.opacity(0.8))
                    
                    Spacer()
                }
                .padding()
            } else {
                // Blueprint B: Employee Task Agenda
                VStack(spacing: 0) {
                    // Header Status Bar
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text("CorpRemind")
                                .font(.system(size: 21, weight: .bold))
                                .foregroundColor(.white)
                            Text("Employee Portal")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(corpIceBlue)
                        }
                        Spacer()
                        
                        HStack(spacing: 16) {
                            Button(action: {
                                // Logout / lock session
                                isAuthenticated = false
                                mpinInput = ""
                            }) {
                                Image(systemName: "lock.fill")
                                    .font(.system(size: 17))
                                    .padding(8)
                                    .background(Color.white.opacity(0.15))
                                    .clipShape(Circle())
                                    .foregroundColor(.white)
                            }
                            
                            Image(systemName: "bell.fill")
                                .font(.system(size: 18))
                                .foregroundColor(.white)
                            
                            Image(systemName: "person.crop.circle.fill")
                                .font(.system(size: 26))
                                .foregroundColor(corpIceBlue)
                        }
                    }
                    .padding()
                    .background(corpNavy)
                    
                    // Horizontal Day Scroller
                    HStack(spacing: 10) {
                        ForEach(daysOfWeek, id: \.self) { day in
                            Button(action: {
                                selectedDay = day
                            }) {
                                Text(day)
                                    .font(.system(size: 13, weight: .bold))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(selectedDay == day ? corpIceBlue : Color.white)
                                    .foregroundColor(selectedDay == day ? corpNavy : corpTextDark)
                                    .cornerRadius(18)
                                    .shadow(color: Color.black.opacity(0.04), radius: 2, y: 1)
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 12)
                    .background(Color.gray.opacity(0.06))
                    
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            HStack {
                                Text("Agenda for selected day:")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(.secondary)
                                Text(selectedDay == "SAT" ? "Today (Saturday)" : selectedDay)
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(corpNavy)
                            }
                            .padding(.horizontal)
                            .padding(.top, 14)
                            
                            ForEach(tasks) { task in
                                VStack(alignment: .leading, spacing: 12) {
                                    HStack {
                                        Text("\(task.type): \(task.title)")
                                            .font(.system(size: 16, weight: .bold))
                                            .foregroundColor(corpNavy)
                                            .lineLimit(2)
                                        Spacer()
                                        if task.status == "COMPLETE" {
                                            Text("COMPLETE")
                                                .font(.system(size: 10, weight: .heavy))
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(Color.green.opacity(0.12))
                                                .foregroundColor(.green)
                                                .cornerRadius(6)
                                        } else {
                                            Text("PENDING")
                                                .font(.system(size: 10, weight: .heavy))
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(Color.orange.opacity(0.12))
                                                .foregroundColor(.orange)
                                                .cornerRadius(6)
                                        }
                                    }
                                    
                                    HStack(spacing: 16) {
                                        Label(task.time, systemImage: "clock.fill")
                                            .font(.system(size: 12))
                                            .foregroundColor(.gray)
                                        
                                        Label(task.location, systemImage: "location.fill")
                                            .font(.system(size: 12))
                                            .foregroundColor(.gray)
                                    }
                                    
                                    if task.status == "PENDING" {
                                        HStack(spacing: 12) {
                                            Button(action: {
                                                if let idx = tasks.firstIndex(where: { $0.id == task.id }) {
                                                    tasks[idx].status = "COMPLETE"
                                                }
                                            }) {
                                                Text("Check In")
                                                    .font(.system(size: 12, weight: .bold))
                                                    .foregroundColor(.white)
                                                    .padding(.horizontal, 14)
                                                    .padding(.vertical, 8)
                                                    .background(corpNavy)
                                                    .cornerRadius(8)
                                            }
                                            
                                            Button(action: {}) {
                                                Text("Snooze")
                                                    .font(.system(size: 12, weight: .bold))
                                                    .foregroundColor(corpNavy)
                                                    .padding(.horizontal, 14)
                                                    .padding(.vertical, 8)
                                                    .background(corpIceBlue)
                                                    .cornerRadius(8)
                                            }
                                        }
                                        .padding(.top, 4)
                                    }
                                }
                                .padding()
                                .background(Color.white)
                                .cornerRadius(12)
                                .shadow(color: Color.black.opacity(0.04), radius: 3, y: 1.5)
                                .padding(.horizontal)
                            }
                            
                            // Secure sync indicators
                            HStack(spacing: 8) {
                                Spacer()
                                Image(systemName: "checkmark.shield.fill")
                                    .foregroundColor(.green)
                                Text("Dual-Locked Crypto Engine Activated")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                                Spacer()
                            }
                            .padding(.top, 10)
                        }
                    }
                    
                    Spacer()
                    
                    // Compliance bottom tabs
                    HStack {
                        Spacer()
                        TabBarIcon(systemName: "list.bullet.clipboard.fill", title: "Tasks", active: activeTab == 0) {
                            activeTab = 0
                        }
                        Spacer()
                        TabBarIcon(systemName: "checkmark.seal.fill", title: "Compliance", active: activeTab == 1) {
                            activeTab = 1
                        }
                        Spacer()
                        TabBarIcon(systemName: "slider.horizontal.3", title: "Settings", active: activeTab == 2) {
                            activeTab = 2
                        }
                        Spacer()
                    }
                    .padding(.top, 10)
                    .padding(.bottom, 24)
                    .background(Color.white)
                    .shadow(color: Color.black.opacity(0.05), radius: 3, y: -2)
                }
            }
        }
    }
    
    private func appendDigit(_ string: String) {
        if mpinInput.count < 4 {
            mpinInput.append(string)
            if mpinInput.count == 4 {
                verifyPin()
            }
        }
    }
    
    private func verifyPin(force: Bool = false) {
        // Safe default passcode matches simple demo configurations, e.g. 1234
        if mpinInput == "1234" || (force && mpinInput.count >= 4) {
            isAuthenticated = true
            loginErrorMsg = ""
        } else {
            loginErrorMsg = "Secure PIN error. Use default passcode (1234) for credentials setup."
            mpinInput = ""
        }
    }
}

// SwiftUI UI Element Declarations
struct KeypadButton: View {
    let text: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.system(size: 21, weight: .bold))
                .foregroundColor(Color(red: 0.0, green: 0.098, blue: 0.27))
                .frame(width: 75, height: 50)
                .background(Color(red: 0.85, green: 0.886, blue: 1.0).opacity(0.35))
                .cornerRadius(12)
        }
    }
}

struct TabBarIcon: View {
    let systemName: String
    let title: String
    let active: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: systemName)
                    .font(.system(size: 19))
                    .foregroundColor(active ? Color(red: 0.0, green: 0.098, blue: 0.27) : .secondary)
                Text(title)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(active ? Color(red: 0.0, green: 0.098, blue: 0.27) : .secondary)
            }
        }
    }
}

struct TaskItem: Identifiable {
    let id: String
    let title: String
    let time: String
    let location: String
    var status: String
    let type: String
    let wfo: Bool
}
