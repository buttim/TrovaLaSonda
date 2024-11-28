package eu.ydiaeresis.trovalasonda

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import eu.ydiaeresis.trovalasonda.databinding.ActivityReportBinding
import java.net.URLEncoder

class ReportActivity:AppCompatActivity() {
    private lateinit var binding:ActivityReportBinding
    private var sondeId=""
    private var lat=0.0
    private var lng=0.0
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_report)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        intent.extras!!.apply {
            sondeId=getString("sondeId")!!
            lat=getDouble("lat")
            lng=getDouble("lng")
        }
        binding.webView.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view:WebView?,url:String?,favicon:Bitmap?) {
                    super.onPageStarted(view,url,favicon)
                    if (url!=null) {
                        if (url.endsWith("/user_panel.php")) {
                            val data="sondenumber="+URLEncoder.encode(sondeId,"UTF-8")
                            view?.postUrl("https://radiosondy.info/user/sonde_edit.php",
                                data.toByteArray())
                        } else if (url.endsWith("/index.php?") || url.contains("/sonde_archive.php?")) {
                            finish()
                        }
                    }
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url!=null) {
                        if (url.endsWith("/sonde_edit.php")) {
                            view?.evaluateJavascript("""
                                javascript: { 
                                    document.getElementById('state').selectedIndex=2
                                    document.getElementsByName('llatitude')[0].value=$lat
                                    document.getElementsByName('llongitude')[0].value=$lng
                                    document.getElementsByName('found_by')[0].focus()
                                }
                            """) {}
                        }
                    }
                    Log.i(FullscreenActivity.TAG,url?:"")
                }
                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse:WebResourceResponse
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    Log.d(FullscreenActivity.TAG,"Risposta: ${request?.url} ${errorResponse.statusCode}")
                    //if (errorResponse.statusCode==404) {
                    //}
                }
            }
            setPadding(0, 0, 0, 0)
            setInitialScale(160)
            settings.javaScriptEnabled=true
            if (savedInstanceState == null)
                loadUrl("https://radiosondy.info/user/login.php?")
        }
    }

    override fun onOptionsItemSelected(item:MenuItem) : Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState:Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

     override fun onRestoreInstanceState(savedInstanceState:Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        binding.webView.restoreState(savedInstanceState)
    }
}