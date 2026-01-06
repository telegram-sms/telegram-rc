# GitHub Copilot Instructions for Telegram Remote Control

## Project Overview

This is an Android application that enables remote control of Android devices via Telegram Bot API. The app is written in Kotlin and uses modern Android development practices.

**Key Technologies:**
- Language: Kotlin 2.3.0
- Min SDK: Android 10 (API 29)
- Target SDK: Android 14+ (API 36)
- Architecture: Service-based with MMKV for persistent storage
- Primary Libraries: OkHttp, Gson, Shizuku, Room Database, MMKV

## Coding Standards and Conventions

### General Guidelines

1. **Language**: All code must be written in Kotlin
2. **Null Safety**: Leverage Kotlin's null safety features; use `?` and `!!` appropriately
3. **Code Style**: Follow Android Kotlin Style Guide
4. **Naming Conventions**:
   - Classes: PascalCase (e.g., `ChatService`, `MainActivity`)
   - Functions: camelCase (e.g., `sendMessage`, `getNetworkType`)
   - Constants: SCREAMING_SNAKE_CASE (e.g., `TAG`, `CHAT_INFO_MMKV_ID`)
   - Private variables: start with lowercase (e.g., `preferences`, `botToken`)

### Android-Specific Patterns

1. **Context Usage**:
   - Use `applicationContext` for long-lived operations
   - Use activity context only when necessary for UI operations
   - Always pass context as parameter instead of storing static references

2. **Permission Handling**:
   - Always check permissions before using protected APIs
   - Use `ActivityCompat.checkSelfPermission()` pattern
   - Log permission denials with appropriate messages

3. **Background Work**:
   - Use Services for long-running operations
   - Implement JobScheduler/WorkManager for scheduled tasks
   - Always acquire WakeLock and WifiLock when needed
   - Release locks in `onDestroy()`

4. **Threading**:
   - Network calls must be on background threads
   - UI updates must use `runOnUiThread { }`
   - Use Kotlin Coroutines when appropriate

### Project-Specific Patterns

#### 1. Telegram API Communication

```kotlin
// Always use this pattern for Telegram API calls
val requestUri = getUrl(botToken, "sendMessage")
val requestBody = RequestMessage().apply {
    chatId = chatID
    messageThreadId = threadID
    text = messageText
    parseMode = "HTML"
}
val body = Gson().toJson(requestBody).toRequestBody(Const.JSON)
val request = Request.Builder().url(requestUri).method("POST", body).build()
```

#### 2. MMKV Storage Access

```kotlin
// Use named MMKV instances for different data types
val preferences = MMKV.mmkvWithID(Const.SETTINGS_MMKV_ID)
val chatInfo = MMKV.mmkvWithID(Const.CHAT_INFO_MMKV_ID)
val statusMMKV = MMKV.mmkvWithID(Const.STATUS_MMKV_ID)
```

#### 3. Shizuku Integration

```kotlin
// Always check Shizuku availability
if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
    // Use Shizuku APIs
}
```

#### 4. Logging

```kotlin
// Use consistent logging pattern
Log.d(Const.TAG, "Description: $variable")
Log.e(Const.TAG, "Error description: ", exception)
Log.i(Const.TAG, "Info message")
Log.w(Const.TAG, "Warning message")
```

#### 5. Message Formatting

```kotlin
// System messages always use this header
requestBody.text = "${getString(R.string.system_message_head)}\n$actualMessage"

// Use HTML formatting for Telegram
parseMode = "HTML"
// Use <code>, <b>, <i> tags as needed
```

#### 6. Error Handling

```kotlin
// Always provide user-friendly error messages
try {
    // operation
} catch (e: Exception) {
    Log.e(Const.TAG, "operation_name: ", e)
    runOnUiThread {
        showErrorDialog("User-friendly error: ${e.message}")
    }
}
```

## File Structure Guidelines

### Activity Classes
- Implement proper lifecycle methods
- Clean up resources in `onDestroy()`
- Use `lateinit var` for views that are initialized in `onCreate()`
- Request permissions appropriately

### Service Classes
- Always call `startForeground()` for foreground services
- Specify service type for Android 14+ (`FOREGROUND_SERVICE_TYPE_*`)
- Implement `onStartCommand()` to return `START_STICKY` for persistent services
- Release all locks and resources in `onDestroy()`

### Static Utility Classes
- Place in `static_class` package
- Use `object` for singletons
- Make functions self-contained with all dependencies passed as parameters

### Data Classes
- Place in `data_structure` package
- Use `@SerializedName` for Gson serialization
- Keep data classes simple (no business logic)

## API Integration Guidelines

### Telegram Bot API

1. **Message Sending**:
   - Always include error handling
   - Check response codes
   - Parse error descriptions from response JSON
   - Log all API calls

2. **Keyboard Markup**:
   - Use `ReplyMarkupKeyboard.getReplyKeyboardMarkup()` for persistent keyboards
   - Use `ReplyMarkupKeyboard.getRemoveKeyboardMarkup()` to remove keyboards
   - Use inline keyboards for temporary actions

3. **Message Threading**:
   - Support `message_thread_id` for topic-based groups
   - Always validate topic ID matches configured value

### Shizuku API

1. **Telephony Operations**:
   - Use `ITelephony` interface via Shizuku
   - Handle dual-SIM scenarios
   - Check subscription validity

2. **Network Operations**:
   - Use `IConnectivityManager` for data control
   - Validate network state before operations

## Common Tasks

### Adding a New Command

1. Add command string to `ChatService.kt` in the `when (command)` block
2. Implement command logic
3. Update help text in `/help` command handler
4. Add keyboard button if using reply keyboard
5. Test with and without required permissions

### Adding a New Setting

1. Add UI element in appropriate Activity layout
2. Add string resources
3. Save to MMKV in save handler
4. Load from MMKV in initialization
5. Use setting in relevant Service

### Adding Keyboard Buttons

1. Modify `ReplyMarkupKeyboard.getReplyKeyboardMarkup()`
2. Add buttons to appropriate row
3. Keep layout clean (max 2-3 buttons per row)
4. Use short, clear command labels

## Testing Considerations

1. **Dual-SIM Testing**: Always test on both single and dual-SIM devices
2. **Permission Testing**: Test with permissions granted and denied
3. **Network Testing**: Test on WiFi, mobile data, and offline scenarios
4. **Shizuku Testing**: Test with Shizuku enabled and disabled
5. **Background Testing**: Verify services survive process death

## Security Guidelines

1. **No Hardcoded Secrets**: Never commit bot tokens or sensitive data
2. **Validate Input**: Always validate user input from Telegram
3. **Chat ID Verification**: Always verify sender chat ID matches configured ID
4. **Secure Storage**: Use MMKV for sensitive data (it's encrypted by default)

## Performance Guidelines

1. **Lazy Initialization**: Initialize heavy objects only when needed
2. **Resource Management**: Always clean up resources (locks, threads, connections)
3. **Efficient Queries**: Use Room database efficiently with proper indexes
4. **Network Efficiency**: Batch operations when possible

## Documentation

1. **Comments**: Add comments for complex logic
2. **KDoc**: Use KDoc for public APIs
3. **TODOs**: Mark incomplete features with `// TODO: description`
4. **Function Purpose**: Document what, not how

## Common Pitfalls to Avoid

1. ❌ Don't use static Context references (memory leaks)
2. ❌ Don't perform network operations on main thread
3. ❌ Don't forget to check permissions before protected APIs
4. ❌ Don't use `lateinit` for nullable fields (use `var ... ? = null` instead)
5. ❌ Don't forget to release WakeLock/WifiLock
6. ❌ Don't assume single-SIM device (always check)
7. ❌ Don't trust user input from Telegram (validate everything)

## Useful Commands and Queries

### Find all services:
```
grep -r "Service()" --include="*.kt"
```

### Find MMKV usage:
```
grep -r "MMKV.mmkvWithID" --include="*.kt"
```

### Find permission checks:
```
grep -r "checkSelfPermission" --include="*.kt"
```

## When Making Changes

1. ✅ Check for existing patterns in the codebase
2. ✅ Update related documentation
3. ✅ Add appropriate error handling
4. ✅ Test on different Android versions if using new APIs
5. ✅ Consider backward compatibility
6. ✅ Log important operations for debugging
7. ✅ Update help text if adding new commands

## Resources

- Project README: `/README.md`
- Build Configuration: `/app/build.gradle`
- Constants: Look for `Const` object in the codebase
- String Resources: `/app/src/main/res/values/strings.xml`

