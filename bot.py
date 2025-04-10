import os
import json
import requests
from telegram import Update
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ConversationHandler, ContextTypes

# Состояния
ASK_DRIVER, ASK_CAR, ASK_PLATE, ASK_DATE, ODO_START, FUEL_NORM, FUEL_START, ROUTE_INPUT, ROUTE_CONFIRM, ODO_END = range(10)

ORS_API_KEY = "5b3ce3597851110001cf624891fb1b2e0e1d43aa89e9147212dc82c1"
GEOCODE_URL = "https://api.openrouteservice.org/geocode/search"
ROUTE_URL = "https://api.openrouteservice.org/v2/directions/driving-car"
USER_PROFILE_FILE = "users.json"

sessions = {}
profiles = {}

def load_profiles():
    global profiles
    if os.path.exists(USER_PROFILE_FILE):
        with open(USER_PROFILE_FILE, "r", encoding="utf-8") as f:
            profiles = json.load(f)

def save_profiles():
    with open(USER_PROFILE_FILE, "w", encoding="utf-8") as f:
        json.dump(profiles, f, ensure_ascii=False, indent=2)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    sessions[user_id] = {"routes": [], "coords": []}
    load_profiles()
    if user_id in profiles:
        await update.message.reply_text("Профиль найден. Введите дату (ГГГГ-ММ-ДД):")
        return ASK_DATE
    else:
        await update.message.reply_text("Введите ФИО водителя:")
        return ASK_DRIVER

async def ask_driver(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    profiles[user_id] = {"driver": update.message.text}
    await update.message.reply_text("Введите марку автомобиля:")
    return ASK_CAR

async def ask_car(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    profiles[user_id]["car"] = update.message.text
    await update.message.reply_text("Введите госномер:")
    return ASK_PLATE

async def ask_plate(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    profiles[user_id]["plate"] = update.message.text
    save_profiles()
    await update.message.reply_text("Введите дату (ГГГГ-ММ-ДД):")
    return ASK_DATE

async def ask_date(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[str(update.effective_user.id)]["date"] = update.message.text
    await update.message.reply_text("Введите начальный пробег (км):")
    return ODO_START

async def ask_odo_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[str(update.effective_user.id)]["odo_start"] = update.message.text
    await update.message.reply_text("Введите расход по норме (л/100км):")
    return FUEL_NORM

async def ask_fuel_norm(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[str(update.effective_user.id)]["fuel_norm"] = update.message.text
    await update.message.reply_text("Введите топливо до выезда (л):")
    return FUEL_START

async def ask_fuel_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    keyboard = ReplyKeyboardMarkup([
        ["Туркменская 14а"],
        ["Готово"]
    ], resize_keyboard=True)
    await update.message.reply_text(
        "Введите адрес. Первый адрес — точка старта. Напишите \"Готово\", когда закончите.",
        reply_markup=keyboard)
    return ROUTE_INPUT

async def input_route(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    text = update.message.text.strip()

    if text.lower() == "готово":
        await update.message.reply_text("Введите конечный пробег:")
        return ODO_END

    params = {
        "apikey": "0a947709-13f1-4dee-9ce9-74ea971cffb0",
        "geocode": text,
        "format": "json",
        "results": 1
    }
    response = requests.get("https://geocode-maps.yandex.ru/1.x/", params=params)
    result = response.json()
    if not result.get("features"):
        await update.message.reply_text("Адрес не найден. Повторите:")
        return ROUTE_INPUT

    pos = result["response"]["GeoObjectCollection"]["featureMember"][0]["GeoObject"]["Point"]["pos"]
    coords = [float(pos.split()[0]), float(pos.split()[1])]
    formatted = result["response"]["GeoObjectCollection"]["featureMember"][0]["GeoObject"]["metaDataProperty"]["GeocoderMetaData"]["text"]
    await update.message.reply_text(f"Подтвердите адрес: {formatted} (да/нет)")
    return ROUTE_CONFIRM

async def confirm_route(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    if update.message.text.lower() != "да":
        await update.message.reply_text("Введите адрес снова:")
        return ROUTE_INPUT

    addr = context.user_data["candidate"]
    sessions[user_id]["coords"].append(addr["coords"])

    if len(sessions[user_id]["coords"]) % 2 == 0:
        headers = {"Authorization": ORS_API_KEY, "Content-Type": "application/json"}
        r = requests.post(ROUTE_URL, json=body, headers=headers)
        meters = r.json()["features"][0]["properties"]["segments"][0]["distance"]
        km = round(meters / 1000, 1)
        km_rounded = round(km / 10) * 10
async def ask_odo_end(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[str(update.effective_user.id)]["odo_end"] = update.message.text
    await update.message.reply_text("✅ Все данные собраны. В дальнейшем будет Excel и PDF.")
    return ConversationHandler.END

async def cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Действие отменено.")
    return ConversationHandler.END

if __name__ == "__main__":
    TOKEN = "8066885623:AAH4DKVqNfqx5OSRwT4LZL9Io_CzG2RgaqI"
    app = ApplicationBuilder().token(TOKEN).build()

    conv = ConversationHandler(
        entry_points=[CommandHandler("start", start)],
        states={
            ASK_DRIVER: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_driver)],
            ASK_CAR: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_car)],
            ASK_PLATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_plate)],
            ASK_DATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_date)],
            ODO_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_odo_start)],
            FUEL_NORM: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_fuel_norm)],
            FUEL_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_fuel_start)],
            ROUTE_INPUT: [MessageHandler(filters.TEXT & ~filters.COMMAND, input_route)],
            ROUTE_CONFIRM: [MessageHandler(filters.TEXT & ~filters.COMMAND, confirm_route)],
            ODO_END: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_odo_end)],
        },
        fallbacks=[CommandHandler("cancel", cancel)]
    )

    app.add_handler(conv)
    app.run_polling()