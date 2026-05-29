# CorpRemind: Enterprise Task & Reminder System
### Client Presentation Portfolio & System Architecture
**Version:** 1.2  
**Target:** Client Review & Integration Board  
**Color System:** CorpNavy (#001945) & CorpIceBlue (#D9E2FF)

---

## 1. Executive Summary

**CorpRemind** is a modern, double-secured, offline-first daily task reminder application tailored specifically for corporate environments. Developed using **Kotlin** and **Jetpack Compose**, the system optimizes corporate productivity by providing targeted portals for Administrators, Team Leads, and Employees.

By replacing scattered communication channels with structured daily and schedule-based reminder tracking, CorpRemind ensures compliance with enterprise goals, facilitates remote work alignment (WFO/WFH), and preserves absolute security through persistent offline local databases mapped directly to secure cloud repositories.

---

## 2. Visual Identity & Design System

Reflective of modern enterprise environments, our design direction avoids generic styling defaults to create an atmosphere of **structured precision**. The application adheres strictly to **Material Design 3 (M3)** guidelines:

*   **Corporate Polished Palette:** 
    *   `CorpNavy` (`#001945`): Deep primary shade establishing weight and professional trust.
    *   `CorpIceBlue` (`#D9E2FF`): Secondary soft cool highlight for container states, selections, and high-frequency UI cards.
    *   `CorpLightBg` (`#FDFBFF`): Crisp eye-safe background supporting sustained reading.
    *   `CorpTextDark` (`#1B1B1F`): High-contrast textual content ensuring optimal color contrast ratios (WCAG AAA compliant).
*   **Accessibility & Ergonomics:**
    *   Every interactive component maintains a minimum **48dp × 48dp touch target** to guarantee high motor accuracy.
    *   Comprehensive **Material Typography** scaling (`sp`) adapts automatically to system-level device preferences.
    *   Generous negative space with an 8dp grid system prevents layout congestion, framing key content elegantly.

---

## 3. High-Fidelity UI Blueprints & Snap Layouts

Since the system operates in real-time on our secure server runtime, the following structured spatial wireframes model the exact high-fidelity visual experience rendered on client devices:

### Blueprint A: Secure MPin Entry (Login Interface)
```
+-------------------------------------------------+
|  [o] CorpRemind               2026-05-23 07:46  |
+-------------------------------------------------+
|                                                 |
|          _                                      |
|         | |  CorpRemind                         |
|         |_|  Secure Gateway                     |
|                                                 |
|   Enter your registered 4-Digit MPin            |
|   to unlock your workspace securely.            |
|                                                 |
|                 [ * ] [ * ] [ - ] [ - ]         |
|                                                 |
|          +---------+---------+---------+        |
|          |    1    |    2    |    3    |        |
|          +---------+---------+---------+        |
|          |    4    |    5    |    6    |        |
|          +---------+---------+---------+        |
|          |    7    |    8    |    9    |        |
|          +---------+---------+---------+        |
|          |  Clear  |    0    |  Enter  |        |
|          +---------+---------+---------+        |
|                                                 |
|           Forgot MPin? [Reset via Security]     |
|                                                 |
|                                                 |
|      Copyright © 2026 Developed by Murtaza      |
+-------------------------------------------------+
```

### Blueprint B: Employee Task Agenda
```
+-------------------------------------------------+
| [o] CorpRemind (Employee Portal)   [Bell] [Profile]|
+-------------------------------------------------+
| Today, Saturday 23 May                          |
| [ Mon ] [ Tue ] [ Wed ] [ Thu ] [ Fri ] { SAT } |
+-------------------------------------------------+
|                                                 |
|  +-------------------------------------------+  |
|  | Daily Task: Morning Scrum & Sync        [!] |  |
|  | Time: 09:00 AM                            |  |
|  | Location: Teams Room / WFH                |  |
|  | Status: [ COMPLETE ] (Checked in from WFO)  |  |
|  +-------------------------------------------+  |
|                                                 |
|  +-------------------------------------------+  |
|  | Scheduled: Weekly Project Status Report    |  |
|  | Target: Leads & Admin Review              |  |
|  | Status: [  PENDING  ]  [Snooze]  [Check In] |  |
|  +-------------------------------------------+  |
|                                                 |
|  +-------------------------------------------+  |
|  | Security Notice: Review Login Protocol     |  |
|  | Status: [  PENDING  ]                      |  |
|  +-------------------------------------------+  |
|                                                 |
+-------------------------------------------------+
```

### Blueprint C: Single-Question Identity Recovery
```
+-------------------------------------------------+
| < Identity Verification                         |
+-------------------------------------------------+
|                                                 |
|   To reset your primary credentials, verify     |
|   your identity.                                |
|                                                 |
|   [!] Account Secure Gateway                    |
|   A single random query from your configured    |
|   security questions has been selected.         |
|                                                 |
|   Question:                                     |
|   "What is your high school mascot?"           |
|                                                 |
|   Your Answer:                                  |
|   +-------------------------------------------+ |
|   | Enter answer here...                      | |
|   +-------------------------------------------+ |
|                                                 |
|   +-------------------------------------------+ |
|   |         VERIFY SECURITY ANSWER            | |
|   +-------------------------------------------+ |
|                                                 |
|   Return to Login Screen                        |
|                                                 |
+-------------------------------------------------+
```

---

## 4. Double-Locked Security Framework

Security is the core component of CorpRemind’s runtime. The application incorporates a two-phase security layer protecting workspace content from unauthorized physical access or endpoint compromise:

### A. Local Credentials & MPin Layout
On first-time entry, users define their secure numeric MPin. Data access operations automatically lock behind this lightweight cryptographic barrier, preventing adjacent devices or local users from browsing tasks without appropriate verification.

### B. High-Fidelity Security Recovery (Intelligent Verification)
Our latest updates refine account recovery to provide a smooth, low-friction, yet highly secure experience:
1.  **Preparation (3 Questions Set):** During profile initialization, users select and record answers to **three independent security questions**.
2.  **Intelligent Recovery (Single-Question Selection):** Upon requesting an MPin reset, rather than presenting a lengthy list of all three questions (which increases user exhaustion and visual noise), the app's recovery engine **randomly selects exactly one configured question**.
3.  **Authentication:** Meeting the challenge requires entering the exact registered answer to this specific question. Once validated, the system automatically redirects the user to the secure **Reset MPin** workspace.

---

## 5. Architectural Portals & Roles

CorpRemind adapts dynamically based on the authenticated user's access token, offering three separate experiences out of a single codebase:

*   **Administrator Portal:**
    *   Register, deactivate, or modify employee accounts.
    *   Monitor company-wide task completions in real-time.
    *   Maintain active records and synchronize settings with the cloud.
*   **Team Lead Portal:**
    *   Construct specific workflows, assigning tasks to individual teammates or global groups.
    *   Schedule triggers: daily, weekly, or custom recurrence slots.
    *   Track visual completion indicators.
*   **Employee Portal:**
    *   Manage personal agendas with custom notifications.
    *   Instantly flag task conditions (WFO/WFH locations).
    *   Configure individual reminder sounds and alarm snoozes.

---

## 6. Offline-First Synchronization Architecture

The infrastructure ensures database integrity under intermittent network environments:

```
+------------------------------------------------------------+
|                       USER INTERFACE                       |
|                       (Jetpack Compose)                    |
+------------------------------------------------------------+
                               |
                               | (Unidirectional Data Flow)
                               v
+------------------------------------------------------------+
|                       VIEWMODEL STATE                      |
|                  (StateFlow & Architecture)                |
+------------------------------------------------------------+
                               |
                               | (Local Execution Query)
                               v
+------------------------------------------------------------+
|                       LOCAL PERSISTENCE                    |
|                        (Room Database)                     |
+------------------------------------------------------------+
                               ^
                               | (Real-time Cloud Sync)
                               v
+------------------------------------------------------------+
|                     CLOUD SYNCHRONIZATION                  |
|                   (Firebase Realtime Sync)                 |
+------------------------------------------------------------+
```

*   **Room Database Backup:** All client interactions are written locally first. The application remains fully functional if offline, queuing edits seamlessly.
*   **Firebase Realtime Engine:** Upon restoring cellular or Wi-Fi connectivity, the queue syncs with the central database, guaranteeing no task records are lost or duplicated.
