#!/usr/bin/env python
import interface

KEYCODE_MENU = 82
KEYCODE_BACK = 4

def press(keycode):
    interface.key_down(keycode)
    interface.key_up(keycode)

def press_menu():
    press(KEYCODE_MENU)

def press_back():
    press(KEYCODE_BACK)
