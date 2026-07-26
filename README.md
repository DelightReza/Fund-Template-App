# 💰 Fund & Expense Tracker (Android App)

A modern, fast, and feature-rich **Group Fund & Expense Management** Android application built with **Kotlin**, **Jetpack Compose**, and **Material Design 3**.

It uses a serverless, zero-database architecture powered by the **GitHub Contents API**, allowing you to store and synchronize your fund ledger (`data.json`) and configuration (`config.json`) directly in your own GitHub repository.

---

## ✨ Key Features

### 📊 **Dashboard & Analytics**
* **Real-time Overview**: Track total fund collections, total expenses, current balance, and individual member settlements.
* **Member Leaderboard & Status**: Instantly see who has paid, who owes money, or who is owed reimbursement.

### 👤 **Member Profiles**
* Detailed breakdown per member showing their total contributions, incurred bills, and net balance.
* Filtered transaction history for individual members.

### ⚙️ **Admin Control Panel**
* **Transaction Management**: Record incoming contributions, bill payments, and cash debt clearings (Credits, Debits, Expenses, Settlements, Distributions).
* **Custom Configuration**: Update site title, currency symbol (e.g. `₹`, `$`, `€`), member list, and expense categories dynamically.

### 🔄 **GitHub Synchronization**
* **Direct GitHub Sync**: Push and pull ledger data directly to/from your GitHub repository using a Personal Access Token (PAT).
* **Caching**: Automatic caching of transactions and configurations to work seamlessly across sessions.

### 🎨 **User Experience & Design**
* **Material Design 3**: Fully embraces Google's modern design language with dynamic colors and Edge-to-Edge layouts.
* **Animations**: Smooth transitions using Jetpack Compose animation APIs.

---

## 🛠️ Tech Stack

* **Framework**: [Android SDK](https://developer.android.com/studio)
* **Language**: [Kotlin](https://kotlinlang.org/)
* **UI Toolkit**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
* **Architecture**: MVVM (Model-View-ViewModel) + Clean Architecture principles
* **Networking**: Core Java/Kotlin networking for GitHub API integrations
* **Serialization**: [Gson](https://github.com/google/gson)
* **Build System**: Gradle (Kotlin DSL)

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio**: Koala Feature Drop or newer recommended.
* **Android SDK**: Minimum API level 26+

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/<your-username>/<your-repo-name>.git
   cd <your-repo-name>
   ```

2. **Open in Android Studio:**
   * Open Android Studio and select "Open an Existing Project".
   * Navigate to the cloned folder and open it.

3. **Build and Run:**
   * Allow Gradle to sync the project.
   * Connect an Android device or start an emulator.
   * Click the "Run" button (green play icon) in Android Studio.

---

## 📁 Project Structure

```
├── app/
│   ├── src/main/java/com/delightreza/fund/
│   │   ├── MainActivity.kt        # Application entry point
│   │   ├── ui/                    # Jetpack Compose UI Screens (HomeScreen, ProfileScreen, AddTransactionScreen, etc.)
│   │   ├── data/                  # Repository and Models (Repository.kt, Models.kt)
│   │   └── theme/                 # Material Design 3 Theme definitions
│   └── src/main/res/              # Android Resources (icons, strings)
├── build.gradle.kts               # Project build configuration
└── settings.gradle.kts            # Project settings
```

---

## 📄 License

This project is open-source and available under the MIT License.
