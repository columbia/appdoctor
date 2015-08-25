#!/usr/bin/env python
import interface
import widget
import keyboard

MENU_FIRST = 1 # Menu.FIRST

def click_by_id(menu_id):
    interface.menu_click(menu_id + MENU_FIRST)

def click(entry_num):
    keyboard.press_menu()
    menu_id = widget.get_widget_id("android:expanded_menu")
    entry_id = interface.get_view_child(menu_id, entry_num)
    interface.click(entry_id)

def click_by_text(text):
    keyboard.press_menu()
    menu_id = widget.get_widget_id("android:expanded_menu")
    entry_id = interface.get_view_child_by_text(menu_id, text)
    interface.click(entry_id)

def click_icon(entry_num):
    keyboard.press_menu()
    menu_id = widget.get_widget_id("android:icon_menu")
    entry_id = interface.get_view_child(menu_id, entry_num)
    interface.click(entry_id)
