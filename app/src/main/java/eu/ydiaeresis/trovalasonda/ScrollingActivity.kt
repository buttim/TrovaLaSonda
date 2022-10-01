package eu.ydiaeresis.trovalasonda

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import eu.ydiaeresis.trovalasonda.databinding.ActivityScrollingBinding

class ScrollingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScrollingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScrollingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarLayout.title = "${title} ${BuildConfig.VERSION_NAME}"
        binding.content.text?.movementMethod= LinkMovementMethod.getInstance()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else
            super.onOptionsItemSelected(item)
    }
}