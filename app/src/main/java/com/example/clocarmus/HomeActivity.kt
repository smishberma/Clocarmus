package com.example.clocarmus

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Optional, nonvisual Home entry. Route to the one player task instead of duplicating its state.
 * An activity-alias to a singleTask activity can still create a second task under the alias name.
 */
class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            .putExtra("home_activation", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
