import logging
import os
import asyncio
from collections import defaultdict
from telegram import Update, ReplyKeyboardMarkup, KeyboardButton
from telegram.ext import (
    ApplicationBuilder, CommandHandler, MessageHandler,
    ConversationHandler, ContextTypes, filters
)
import requests
from openpyxl import load_workbook
from datetime import datetime

(ASK_DATE, ASK_NAME, ASK_CAR, ASK_START_ODOMETER, ASK_FUEL_CONSUMPTION,
 ASK_FUEL_BEFORE_TRIP, ASK_ROUTE, ASK_END_ODOMETER, CONFIRMATION) = range(9)

user_data_store = defaultdict(dict)
frequent_addresses = defaultdict(lambda: {"Туркменская, 14а": 1})

YANDEX_API_KEY = "0a947709-13f1-4dee-9ce9-74ea971cffb0"
ORS_API_KEY = "5b3ce3597851110001cf624891fb1b2e0e1d43aa89e9147212dc82c1"

def get_start_keyboard():
    return ReplyKeyboardMarkup(
        [[KeyboardButton("Сегодня")], [KeyboardButton("Ввести вручную")]],
        resize_keyboard=True, one_time_keyboard=True
    )

def get_confirmation_keyboard():
    return ReplyKeyboardMarkup(
        [[KeyboardButton("Да"), KeyboardButton("Нет")]],
        resize_keyboard=True, one_time_keyboard=True
    )

def get_address_keyboard(user_id):
    addresses = sorted(frequent_addresses[user_id].items(), key=lambda x: -x[1])
    keyboard = [[KeyboardButton(addr[0])] for addr in addresses[:3]]
    return ReplyKeyboardMarkup(keyboard, resize_keyboard=True)

def geocode_yandex(address):
    url = "https://geocode-maps.yandex.ru/1.x/"
    params = {
        "apikey": YANDEX_API_KEY,
        "geocode": f"Волгоградская область, Россия, {address}",
        "format": "json",
        "lang": "ru_RU"
    }
    try:
        resp = requests.get(url, params=params).json()
        pos = resp["response"]["GeoObjectCollection"]["featureMember"][0]["GeoObject"]["Point"]["pos"]
        lon, lat = pos.split()
        return address, (float(lat), float(lon))
    except:
        return None, None

async def async_geocode_yandex(address):
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, geocode_yandex, address)

def get_route_distance(start_coords, end_coords):
    url = "https://api.openrouteservice.org/v2/directions/driving-car"
    headers = {"Authorization": ORS_API_KEY}
    payload = {
        "coordinates": [[start_coords[1], start_coords[0]], [end_coords[1], end_coords[0]]]
    }
    try:
        resp = requests.post(url, json=payload, headers=headers)
        data = resp.json()
        meters = data["features"][0]["properties"]["segments"][0]["distance"]
        return round(meters / 1000, 1)
    except:
        return 0

async def get_distance_ors(start_coords, end_coords):
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, get_route_distance, start_coords, end_coords)

# Остальная логика (обработчики состояний, генерация Excel и main()) сохранены как в предыдущей версии...