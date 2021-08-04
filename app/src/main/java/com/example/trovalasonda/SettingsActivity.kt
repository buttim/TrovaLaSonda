package com.example.trovalasonda

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private var spLcd:Spinner?=null
    private var etCall: EditText?=null
    private var etSDA: EditText?=null
    private var etSCL: EditText?=null
    private var etRST: EditText?=null
    private var etBUZ: EditText?=null
    private var etLED: EditText?=null
    private var etBAT: EditText?=null
    private var etBatMin: EditText?=null
    private var etBatMax: EditText?=null
    private var spBatType: Spinner?=null
    private var spRS41bw: Spinner?=null
    private var spM20bw: Spinner?=null
    private var spM10bw: Spinner?=null
    private var spPILOTbw: Spinner?=null
    private var spDFMbw: Spinner?=null
    private var spNAM: Spinner?=null
    private var etOffset: EditText?=null
    var sda=0
    var scl=0
    var rst=0
    var lcd=0
    var buz=0
    var led=0
    var bat=0
    var batMin=0
    var batMax=0
    var batType=0
    var RS41bw=0
    var M20bw=0
    var M10bw=0
    var PILOTbw=0
    var DFMbw=0
    var offset=0
    var call=""
    var nam=0

    private fun setFields() {
        spLcd?.setSelection(lcd)
        etCall?.setText(call)
        etSDA?.setText(sda.toString())
        etSCL?.setText(scl.toString())
        etRST?.setText(rst.toString())
        etBUZ?.setText(buz.toString())
        etLED?.setText(led.toString())
        etBAT?.setText(bat.toString())
        etBatMin?.setText(batMin.toString())
        etBatMax?.setText(batMax.toString())
        spBatType?.setSelection(batType)
        spRS41bw?.setSelection(RS41bw)
        spM20bw?.setSelection(M20bw)
        spM10bw?.setSelection(M10bw)
        spPILOTbw?.setSelection(PILOTbw)
        spDFMbw?.setSelection(DFMbw)
        spNAM?.setSelection(nam)
        etOffset?.setText(offset.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val extras= intent.extras
        sda=extras?.getInt("sda")?:0
        scl=extras?.getInt("scl")?:0
        rst=extras?.getInt("rst")?:0
        lcd=extras?.getInt("lcd")?:0
        buz=extras?.getInt("buz")?:0
        led=extras?.getInt("led")?:0
        bat=extras?.getInt("bat")?:0
        batMin=extras?.getInt("batMin")?:0
        batMax=extras?.getInt("batMax")?:0
        batType=extras?.getInt("batType")?:0
        RS41bw=extras?.getInt("RS41bw")?:0
        M20bw=extras?.getInt("M20bw")?:0
        M10bw=extras?.getInt("M10bw")?:0
        PILOTbw=extras?.getInt("PILOTbw")?:0
        DFMbw=extras?.getInt("DFMbw")?:0
        offset=extras?.getInt("offset")?:0
        call=extras?.getString("call")?:""
        nam=extras?.getInt("nam")?:0

        spLcd=findViewById(R.id.lcd)
        etCall=findViewById(R.id.call)
        etSDA=findViewById(R.id.sda)
        etSCL=findViewById(R.id.scl)
        etCall=findViewById(R.id.call)
        etRST=findViewById(R.id.rst)
        etBUZ=findViewById(R.id.buz)
        etLED=findViewById(R.id.led)
        etBAT=findViewById(R.id.bat)
        etBatMin=findViewById(R.id.bat_min)
        etBatMax=findViewById(R.id.bat_max)
        spBatType=findViewById(R.id.bat_type)
        spRS41bw=findViewById(R.id.rs41bw)
        spM20bw=findViewById(R.id.m20bw)
        spM10bw=findViewById(R.id.m10bw)
        spPILOTbw=findViewById(R.id.pilbw)
        spDFMbw=findViewById(R.id.dfmbw)
        spNAM=findViewById(R.id.nam)
        etOffset=findViewById(R.id.offset)

        setFields()

        findViewById<Button>(R.id.tune).setOnClickListener { view ->
            Toast.makeText(this, "Not implemented (yet)",Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.reset).setOnClickListener { view ->
            spLcd?.setSelection(0)
            etCall?.setText("MYCALL")
            etSDA?.setText("21")
            etSCL?.setText("22")
            etRST?.setText("16")
            etBUZ?.setText("0")
            etLED?.setText("25")
            etBAT?.setText("35")
            etBatMin?.setText("2950")
            etBatMax?.setText("4180")
            spBatType?.setSelection(0)
            spRS41bw?.setSelection(1)
            spM20bw?.setSelection(7)
            spM10bw?.setSelection(7)
            spPILOTbw?.setSelection(7)
            spNAM?.setSelection(0)
            etOffset?.setText("0")
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            val data=Intent().apply {
                var t=spLcd?.selectedItemPosition?:0
                if (t!=lcd) putExtra("lcd",t)
                val s=etCall?.text.toString()?:""
                if (s!=call) putExtra("call",s)
                t=etSDA?.text.toString().toInt()
                if (t!=sda) putExtra("sda",t)
                t=etSCL?.text.toString().toInt()
                if (t!=scl) putExtra("scl",t)
                t=etRST?.text.toString().toInt()
                if (t!=rst) putExtra("rst",t)
                t=etBUZ?.text.toString().toInt()
                if (t!=buz) putExtra("buz",t)
                t=etLED?.text.toString().toInt()
                if (t!=led) putExtra("led",t)
                t=etBAT?.text.toString().toInt()
                if (t!=bat) putExtra("bat",t)
                t=etBatMin?.text.toString().toInt()
                if (t!=batMin) putExtra("batMin",t)
                t=etBatMax?.text.toString().toInt()
                if (t!=batMax) putExtra("batMax",t)
                t=spBatType?.selectedItemPosition?:0
                if (t!=batType) putExtra("batType",t)
                t=spRS41bw?.selectedItemPosition?:0
                if (t!=RS41bw) putExtra("RS41bw",t)
                t=spM20bw?.selectedItemPosition?:0
                if (t!=M20bw) putExtra("M20bw",t)
                t=spM10bw?.selectedItemPosition?:0
                if (t!=M10bw) putExtra("M10bw",t)
                t=spPILOTbw?.selectedItemPosition?:0
                if (t!=PILOTbw) putExtra("PILOTbw",t)
                t=spDFMbw?.selectedItemPosition?:0
                if (t!=DFMbw) putExtra("DFMbw",t)
                t=spNAM?.selectedItemPosition?:0
                if (t!=nam) putExtra("nam",t)
                t=etOffset?.text.toString().toInt()
                if (t!=offset) putExtra("offset",t)
            }
            setResult(Activity.RESULT_OK,data)
            this.finish()
        }
    }
    override fun onOptionsItemSelected(item: MenuItem) : Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                this.finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item);
    }
}