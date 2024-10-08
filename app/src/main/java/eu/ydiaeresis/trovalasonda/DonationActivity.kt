package eu.ydiaeresis.trovalasonda

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import eu.ydiaeresis.trovalasonda.databinding.ActivityDonationBinding

class DonationActivity:AppCompatActivity() {
    private lateinit var binding:ActivityDonationBinding

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)

        binding=ActivityDonationBinding.inflate(layoutInflater).apply {
            setContentView(root)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            contentDonation.text.movementMethod=LinkMovementMethod.getInstance()
        }
    }

    override fun onOptionsItemSelected(item:MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else
            super.onOptionsItemSelected(item)
    }
}