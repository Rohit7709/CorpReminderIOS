import UIKit
import Shared
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        // Initialize KMP engine
        let setup = IOSAppSetup()
        print(setup.getMessage())
        
        // Request notifications permission on iOS to monitor corporate compliance tasks
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if granted {
                print("CorpRemind Notification permission granted.")
                self.scheduleWelcomeNotification()
            } else if let error = error {
                print("Notification permission error: \(error.localizedDescription)")
            }
        }
        
        return true
    }
    
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Render notification alerts inside foreground states of corporate tasks
        completionHandler([.banner, .list, .sound])
    }
    
    private func scheduleWelcomeNotification() {
        let center = UNUserNotificationCenter.current()
        
        let content = UNMutableNotificationContent()
        content.title = "CorpRemind Alerts"
        content.body = "Welcome to CorpRemind for iOS! Your daily corporate compliance tasks are ready."
        content.sound = .default
        
        // Push welcome task alert in 5 seconds
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false)
        let request = UNNotificationRequest(identifier: "welcome_alert_ios", content: content, trigger: trigger)
        
        center.add(request) { error in
            if let error = error {
                print("Failed to schedule notification: \(error.localizedDescription)")
            }
        }
    }
}
