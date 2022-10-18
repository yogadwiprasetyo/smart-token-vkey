package id.co.sistema.vkey

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.vkey.android.vguard.VGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.broadcast
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        supportActionBar?.hide()
    }

    /**
     * Method to retrieve [VGuard.getIsVosStarted] from [CustomApplication] class
     * after being broadcast by [EventBus].
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(isStarted: Boolean?) {
        Log.d("Splash", "onMessageEvent-isVosStarted: $isStarted")
        if (isStarted == true) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    /**
     * Handle [EventBus] lifecycle.
     */
    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    /**
     * Handle [EventBus] lifecycle.
     */
    override fun onPause() {
        super.onPause()
        EventBus.getDefault().unregister(this)
    }
}