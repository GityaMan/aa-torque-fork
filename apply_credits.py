#!/usr/bin/env python3
"""Пункт 3: окно «Авторы». Запускать из корня репозитория: python3 apply_credits.py"""
import glob, os, re, sys

LAYOUT = '''<?xml version="1.0" encoding="utf-8"?>
<androidx.appcompat.widget.LinearLayoutCompat
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:layout_margin="5dp"
    xmlns:android="http://schemas.android.com/apk/res/android">

    <TextView
        android:id="@+id/textStopwatch2"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:text="@string/credits_modifications" />

    <TextView
        android:id="@+id/myName"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Echo"
        android:layout_marginBottom="10dp" />

    <TextView
        android:id="@+id/textOriginalLabel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:text="@string/credits_original" />

    <TextView
        android:id="@+id/textOriginalName"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Kyle Agronick"
        android:layout_marginBottom="10dp" />

    <TextView
        android:id="@+id/textStopwatch"
        android:layout_width="wrap_content"
        android:text="@string/credits_author"
        android:textStyle="bold"
        android:layout_height="wrap_content"/>

    <TextView
        android:id="@+id/textView4"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Chillout"
        android:layout_marginBottom="10dp" />

    <TextView
        android:id="@+id/textView2"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:text="@string/credits_basedon" />

    <TextView
        android:id="@+id/textView5"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Martoreto"
        android:layout_marginBottom="10dp" />

    <TextView
        android:id="@+id/textView6"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:text="@string/credits_thanks"/>

    <TextView
        android:id="@+id/textView7"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="10dp"
        android:text="mkwatchface, Ddemtlapps, Hugo Morais, Jose Alberto Morales Nery, kacnje, Mirco, Paul Rusu, tasos-kom, Vasyl Solevych, Vincent chouquet, Vladi69"/>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/icon_credits"
        android:textStyle="bold" />

    <LinearLayout
        android:id="@+id/ic_credits_list"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:layout_marginBottom="20dp"
        ></LinearLayout>

    <TextView
        android:id="@+id/txtForkNotice"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/credits_fork_notice"
        android:textSize="12sp"
        android:layout_marginBottom="20dp" />

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:layout_marginBottom="20dp">

        <androidx.appcompat.widget.AppCompatButton
            android:id="@+id/githubBtn"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:drawableLeft="@drawable/github"
            android:text=" Github"/>

        <androidx.appcompat.widget.AppCompatButton
            android:id="@+id/githubOrigBtn"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/credits_original_btn" />

        <androidx.appcompat.widget.AppCompatButton
            android:id="@+id/translateBtn"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/translate" />

    </LinearLayout>

    <TextView
        android:id="@+id/txtVersion"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/app_version"
        android:layout_marginBottom="20dp"
        android:textAlignment="center"/>
</androidx.appcompat.widget.LinearLayoutCompat>
'''

KOTLIN = '''package com.aatorque.stats

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import timber.log.Timber

class CreditsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.i("onCreateView")
        return inflater.inflate(R.layout.fragment_credits, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Ссылка на ваш репозиторий берётся из build.gradle (ghOwner/ghPackage)
        btnOpenUrl(R.id.githubBtn, BuildConfig.REPO_URL)
        // Оригинальный проект: AA Torque (GPL-3.0)
        btnOpenUrl(R.id.githubOrigBtn, "https://github.com/agronick/aa-torque")
        btnOpenUrl(R.id.translateBtn, "https://poeditor.com/join/project/yttme0y1VZ")
        val iconCredits = view.findViewById<LinearLayout>(R.id.ic_credits_list)
        resources.getStringArray(R.array.ic_credits_items).forEach {
            val tv = TextView(requireContext())
            tv.text = Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT)
            tv.linksClickable = true
            iconCredits.addView(tv)
        }
    }

    fun btnOpenUrl(res: Int, url: String) {
        requireView().findViewById<View>(res).setOnClickListener { _: View? ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
'''

STRINGS = {
    'values': {
        'credits_original': 'Original AA Torque by:',
        'credits_original_btn': 'Original',
        'credits_fork_notice': 'Modified version of AA Torque, licensed under GPL-3.0. '
                               'Changes: direct ELM327 connection, Mazda SkyActiv-D data, '
                               'renamed app. © 2026 Echo.',
    },
    'values-ru': {
        'credits_modifications': 'Разработчик:',
        'credits_original': 'Оригинальный AA Torque:',
        'credits_original_btn': 'Оригинал',
        'credits_fork_notice': 'Изменённая версия AA Torque под лицензией GPL-3.0. '
                               'Изменения: прямое подключение к ELM327, данные Mazda SkyActiv-D, '
                               'новое название. © 2026 Echo.',
    },
}

README_MARK = '<!-- fork-notice -->'
README_NOTICE = (README_MARK + '\n'
    '> **AA Mazda Monitor** is a modified fork of [AA Torque](https://github.com/agronick/aa-torque) '
    'by Kyle Agronick, licensed under GPL-3.0 (see `LICENSE.md`). '
    'Modified in 2026 by Echo: direct ELM327 connection, Mazda SkyActiv-D data, renamed app. '
    'Icons are credited in the in-app Credits screen (CC BY 3.0, Noun Project).\n\n')

def die(msg):
    print('ОШИБКА:', msg); sys.exit(1)

def write(path, text):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    print('записан', path)

# 1. layout
layout = 'app/src/main/res/layout/fragment_credits.xml'
if not os.path.exists(layout):
    die('не найден ' + layout + ' (запускайте из корня репозитория)')
write(layout, LAYOUT)

# 2. CreditsFragment.kt
kt = glob.glob('app/src/**/CreditsFragment.kt', recursive=True)
if len(kt) != 1:
    die('CreditsFragment.kt найден %d раз' % len(kt))
write(kt[0], KOTLIN)

# 3. строки
for folder, items in STRINGS.items():
    path = 'app/src/main/res/%s/strings.xml' % folder
    if not os.path.exists(path):
        die('нет ' + path)
    text = open(path, encoding='utf-8').read()
    add = ''
    for name, value in items.items():
        if 'name="%s"' % name in text:
            print('уже есть', folder, name); continue
        value = value.replace("'", "\\'")
        add += '    <string name="%s">%s</string>\n' % (name, value)
    if add:
        idx = text.rfind('</resources>')
        if idx < 0: die('нет </resources> в ' + path)
        write(path, text[:idx] + add + text[idx:])

# 4. BuildConfig.REPO_URL
gradle = 'app/build.gradle'
text = open(gradle, encoding='utf-8').read()
if '"REPO_URL"' in text:
    print('REPO_URL уже есть')
else:
    m = re.search(r'^([ \t]*)buildConfigField "String", "RELEASE_URL".*$', text, re.M)
    if not m: die('не найдена строка RELEASE_URL в app/build.gradle')
    line = m.group(1) + 'buildConfigField "String", "REPO_URL", "\\"https://github.com/${ghOwner}/${ghPackage}\\""'
    text = text[:m.end()] + '\n' + line + text[m.end():]
    write(gradle, text)

# 5. README
if os.path.exists('README.md'):
    text = open('README.md', encoding='utf-8').read()
    if README_MARK not in text:
        write('README.md', README_NOTICE + text)
    else:
        print('README уже содержит пометку')

print('\nГотово.')
