import android.content.Context
import android.util.Log
import com.qwe7002.telegram_rc.database.AppDatabase
import kotlinx.coroutines.runBlocking

object YellowPage {
    @JvmStatic
    fun checkPhoneNumberInDatabaseBlocking(context: Context, phoneNumber: String): String? {
        return try {
            runBlocking {
                val db = AppDatabase.getDatabase(context)
                val phoneNumbers = db.phoneNumberDao().getPhoneNumbersByPhoneNumber(phoneNumber)
                if (phoneNumbers.isNotEmpty()) {
                    val organization =
                        db.organizationDao().getOrganizationById(phoneNumbers[0].organizationId)
                    organization?.organization
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("SMSReceiver", "Error checking phone number in database", e)
            null
        }
    }
}
