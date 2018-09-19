package com.integer.ffmpeg.app.activity

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.integer.ffmpeg.R


class MainActivity : AppCompatActivity() {



    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        savedInstanceState ?: supportFragmentManager.beginTransaction().replace(R.id.container, CaptureFragment.newInstance()).commit()
        savedInstanceState ?: supportFragmentManager.beginTransaction().replace(R.id.container, CaptureFragmentV2.newInstance()).commit()
    }


}
