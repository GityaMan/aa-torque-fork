#!/usr/bin/env python3
"""Кнопка «Подключение к OBD» (значок ELM327) рядом с иконкой информации.
Запускать из корня репозитория: python3 apply_menu.py"""
import glob, os, re, sys

ICON = '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:fillType="evenOdd"
        android:pathData="M3,3 L21,3 L19,10 L5,10 Z M6.2,4.4 h1.6 v1.4 h-1.6 z M9.2,4.4 h1.6 v1.4 h-1.6 z M12.2,4.4 h1.6 v1.4 h-1.6 z M15.2,4.4 h1.6 v1.4 h-1.6 z M7.7,7.4 h1.6 v1.4 h-1.6 z M10.7,7.4 h1.6 v1.4 h-1.6 z M13.7,7.4 h1.6 v1.4 h-1.6 z M6,12 h12 a1,1 0 0 1 1,1 v7 a1,1 0 0 1 -1,1 h-12 a1,1 0 0 1 -1,-1 v-7 a1,1 0 0 1 1,-1 z M7.4,16.5 a1.1,1.1 0 1 0 2.2,0 a1.1,1.1 0 1 0 -2.2,0 z M11.5,15.6 h5 v1.8 h-5 z" />
</vector>
'''

MENU_ITEM = '''<item
        android:id="@+id/action_obd"
        android:title="@string/action_obd"
        app:showAsAction="ifRoom"
        android:icon="@drawable/ic_elm327"/>
    '''

BRANCH = '''R.id.action_obd -> {
                startActivity(Intent(this, com.aatorque.obd.ObdSettingsActivity::class.java))
                true
            }

            '''

STRINGS = {
    'values': {'action_obd': 'OBD connection'},
    'values-ru': {'action_obd': 'Подключение к OBD'},
}

def die(msg):
    print('ОШИБКА:', msg); sys.exit(1)

def write(path, text):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    print('записан', path)

# 1. значок
os.makedirs('app/src/main/res/drawable', exist_ok=True)
write('app/src/main/res/drawable/ic_elm327.xml', ICON)

# 2. пункт меню перед значком информации
menu = 'app/src/main/res/menu/settings_menu.xml'
if not os.path.exists(menu): die('не найден ' + menu + ' (запускайте из корня репозитория)')
text = open(menu, encoding='utf-8').read()
if 'action_obd' in text:
    print('пункт меню уже есть')
else:
    m = re.search(r'<item\s+android:id="@\+id/action_credits"', text)
    if not m: die('в меню не найден action_credits')
    write(menu, text[:m.start()] + MENU_ITEM + text[m.start():])

# 3. строки
for folder, items in STRINGS.items():
    path = 'app/src/main/res/%s/strings.xml' % folder
    if not os.path.exists(path): die('нет ' + path)
    text = open(path, encoding='utf-8').read()
    add = ''.join('    <string name="%s">%s</string>\n' % (k, v) for k, v in items.items()
                  if 'name="%s"' % k not in text)
    if add:
        idx = text.rfind('</resources>')
        if idx < 0: die('нет </resources> в ' + path)
        write(path, text[:idx] + add + text[idx:])
    else:
        print('строки уже есть:', folder)

# 4. обработчик в SettingsActivity
files = glob.glob('app/src/**/SettingsActivity.kt', recursive=True)
if len(files) != 1: die('SettingsActivity.kt найден %d раз' % len(files))
text = open(files[0], encoding='utf-8').read()
if 'R.id.action_obd' in text:
    print('обработчик уже есть')
else:
    m = re.search(r'R\.id\.action_credits -> \{', text)
    if not m: die('в SettingsActivity не найден R.id.action_credits')
    write(files[0], text[:m.start()] + BRANCH + text[m.start():])

print('\nГотово.')
