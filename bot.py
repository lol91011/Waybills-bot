import logging
import os
import requests
from datetime import datetime
from collections import defaultdict
from telegram import Update, ReplyKeyboardMarkup, KeyboardButton
from telegram.ext import (
    ApplicationBuilder, CommandHandler, MessageHandler, filters,
    ConversationHandler, ContextTypes
)
from openpyxl import load_workbook

logging.basicConfig(level=logging.INFO)

(ASK_DATE, ASK_NAME, ASK_CAR, ASK_ODOMETER_START, ASK_ROUTE, ASK_ODOMETER_END,
 ASK_FUEL_CONSUMPTION, CONFIRM_ROUTE) = range(8)

user_data_store = defaultdict(dict)

YANDEX_API_KEY = "0a947709-13f1-4dee-9ce9-74ea971cffb0"
ORS_API_KEY = "5b3ce3597851110001cf624891fb1b2e0e1d43aa89e9147212dc82c1"

def get_keyboard(options):
    return ReplyKeyboardMarkup([[KeyboardButton(opt)] for opt in options], resize_keyboard=True)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Выберите дату:", reply_markup=get_keyboard(["Сегодня", "Ввести вручную"]))
    return ASK_DATE

async def ask_name(update: Update, context: ContextTypes.DEFAULT_TYPE):
    date_text = update.message.text.strip()
    context.user_data["date"] = datetime.today().strftime("%d.%m.%Y") if "сегодня" in date_text.lower() else date_text
    await update.message.reply_text("Введите ФИО водителя:")
    return ASK_NAME

async def ask_car(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["name"] = update.message.text.strip()
    await update.message.reply_text("Введите марку и номер автомобиля:")
    return ASK_CAR

async def ask_odometer_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["car"] = update.message.text.strip()
    await update.message.reply_text("Введите начальный пробег:")
    return ASK_ODOMETER_START

async def ask_route(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["odometer_start"] = update.message.text.strip()
    context.user_data["routes"] = []
    context.user_data["parsed_routes"] = []
    await update.message.reply_text("Введите адрес маршрута (по одному). Напишите 'готово' когда закончите:")
    return ASK_ROUTE

async def confirm_route(update: Update, context: ContextTypes.DEFAULT_TYPE):
    msg = update.message.text.strip()
    if msg.lower() == "готово":
        await update.message.reply_text("Введите конечный пробег:")
        return ASK_ODOMETER_END

    context.user_data["routes"].append(msg)
    if len(context.user_data["routes"]) > 1:
        from_, to = context.user_data["routes"][-2], context.user_data["routes"][-1]
        from_coord = await geocode_yandex(from_)
        to_coord = await geocode_yandex(to)
        if from_coord and to_coord:
            dist = await get_distance_ors(from_coord, to_coord)
            rounded = int(round(dist / 10.0) * 10)
            context.user_data["parsed_routes"].append({
                "from": from_,
                "to": to,
                "rounded_km": rounded
            })
            await update.message.reply_text(f"{from_} → {to}: {rounded} км")
    return ASK_ROUTE

async def ask_odometer_end(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["odometer_end"] = update.message.text.strip()
    await update.message.reply_text("Введите расход топлива (по норме):")
    return ASK_FUEL_CONSUMPTION

async def finish(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["fuel"] = update.message.text.strip()
    path = generate_excel(context.user_data)
    await update.message.reply_document(open(path, "rb"))
    await update.message.reply_text("Путевой лист готов!")
    return ConversationHandler.END

def generate_excel(data):
    template = "plist_template.xlsx"
    output = f"waybill_{data['name'].split()[0]}_{data['date'].replace('.', '-')}.xlsx"
    wb = load_workbook(template)
    ws = wb.active

    ws["M12"] = data["name"]
    ws["V10"] = data["car"].split()[0]
    ws["AL11"] = data["car"]
    ws["BU19"] = int(data["odometer_start"])
    ws["BT45"] = int(data["odometer_end"])
    ws["BT37"] = 0
    ws["BT38"] = 0
    ws["BT39"] = float(data["fuel"])

    for i, entry in enumerate(data["parsed_routes"]):
        row = 5 + i
        ws[f"E{row}"] = entry["from"]
        ws[f"H{row}"] = entry["to"]
        ws[f"O{row}"] = entry["rounded_km"]

    wb.save(output)
    return output

async def geocode_yandex(address):
    url = "https://geocode-maps.yandex.ru/1.x/"
    params = {
        "apikey": YANDEX_API_KEY,
        "geocode": f"Волгоградская область, Россия, {address}",
        "format": "json"
    }
    r = requests.get(url, params=params)
    try:
        pos = r.json()["response"]["GeoObjectCollection"]["featureMember"][0]["GeoObject"]["Point"]["pos"]
        lon, lat = map(float, pos.split())
        return [lat, lon]
    except:
        return None

async def get_distance_ors(coord1, coord2):
    url = "https://api.openrouteservice.org/v2/directions/driving-car"
    headers = {"Authorization": ORS_API_KEY}
    body = {"coordinates": [coord1[::-1], coord2[::-1]]}
    r = requests.post(url, json=body, headers=headers)
    try:
        meters = r.json()["features"][0]["properties"]["segments"][0]["distance"]
        return round(meters / 1000, 1)
    except:
        return 0

def main():
    app = ApplicationBuilder().token("8066885623:AAH4DKVqNfqx5OSRwT4LZL9Io_CzG2RgaqI").build()
    conv_handler = ConversationHandler(
        entry_points=[CommandHandler("start", start)],
        states={
            ASK_DATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_name)],
            ASK_NAME: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_car)],
            ASK_CAR: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_odometer_start)],
            ASK_ODOMETER_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_route)],
            ASK_ROUTE: [MessageHandler(filters.TEXT & ~filters.COMMAND, confirm_route)],
            ASK_ODOMETER_END: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_odometer_end)],
            ASK_FUEL_CONSUMPTION: [MessageHandler(filters.TEXT & ~filters.COMMAND, finish)],
        },
        fallbacks=[]
    )
    app.add_handler(conv_handler)
    app.run_polling()

if __name__ == "__main__":
    main()