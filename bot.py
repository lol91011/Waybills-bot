import os
import json
import requests
from telegram import Update
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ConversationHandler, ContextTypes
DATE, DRIVER, CAR_MAKE, CAR_PLATE, ODO_START, FUEL_NORM, FUEL_START, ROUTE_INPUT, ROUTE_CONFIRM, ODO_END = range(10)
ORS_API_KEY = "5b3ce3597851110001cf624891fb1b2e0e1d43aa89e9147212dc82c1"
GEOCODE_URL = "https://api.openrouteservice.org/geocode/search"
DIRECTIONS_URL = "https://api.openrouteservice.org/v2/directions/driving-car"
PROFILE_PATH = "users.json"
user_sessions = {}
# Загрузка/сохранение профиля
def load_profiles():
    if not os.path.exists(PROFILE_PATH):
        return {}
    with open(PROFILE_PATH, "r", encoding="utf-8") as f:
        return json.load(f)
def save_profiles(profiles):
    with open(PROFILE_PATH, "w", encoding="utf-8") as f:
        json.dump(profiles, f, indent=2, ensure_ascii=False)
profiles = load_profiles()
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    user_sessions[user_id] = {"routes": [], "coordinates": []}
    if user_id in profiles:
        await update.message.reply_text("Привет! Используем ваш сохранённый профиль.")
        return await ask_date(update, context)
    else:
        await update.message.reply_text("Привет! Введите ФИО водителя:")
        return DRIVER
    user_id = str(update.effective_user.id)
    profiles[user_id] = {"driver": update.message.text}
    save_profiles(profiles)
    await update.message.reply_text("Введите марку автомобиля:")
    return CAR_MAKE
async def car_make(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    profiles[user_id]["car_make"] = update.message.text
    save_profiles(profiles)
    await update.message.reply_text("Введите госномер автомобиля:")
    return CAR_PLATE
async def car_plate(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    profiles[user_id]["car_plate"] = update.message.text
    save_profiles(profiles)
    return await ask_date(update, context)
async def ask_date(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите дату (ГГГГ-ММ-ДД):")
    return DATE
async def get_date(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_sessions[str(update.effective_user.id)]["date"] = update.message.text
    await update.message.reply_text("Введите начальный пробег:")
    return ODO_START
async def get_odo_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_sessions[str(update.effective_user.id)]["odo_start"] = update.message.text
    await update.message.reply_text("Расход по норме (л/100 км):")
    return FUEL_NORM
async def get_fuel_norm(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_sessions[str(update.effective_user.id)]["fuel_norm"] = update.message.text
    await update.message.reply_text("Остаток топлива до выезда:")
    return FUEL_START
async def get_fuel_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Вводите адреса маршрута по одному. Первый — начальная точка. Напишите 'готово', когда закончите.")
    return ROUTE_INPUT
async def get_route_input(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text.strip()
    user_id = str(update.effective_user.id)
    if text.lower() == "готово":
        await update.message.reply_text("Введите конечный пробег:")
        return ODO_END
    params = {
        "api_key": ORS_API_KEY,
        "text": text,
        "boundary.country": "RU",
        "size": 1
    }
    r = requests.get(GEOCODE_URL, params=params)
    data = r.json()
    if not data["features"]:
        await update.message.reply_text("Адрес не найден. Введите снова:")
        return ROUTE_INPUT
    feature = data["features"][0]
    formatted = feature["properties"]["label"]
    coords = feature["geometry"]["coordinates"]
    context.user_data["last_address"] = {
        "text": text,
        "confirmed": False,
        "formatted": formatted,
        "coords": coords
    }
    await update.message.reply_text(
        f"Найдено: {formatted}\nЭто правильный адрес? (да/нет)"
    )
    return ROUTE_CONFIRM
async def confirm_route(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    answer = update.message.text.lower()
    if answer == "да":
        addr = context.user_data["last_address"]
        user_sessions[user_id]["routes"].append(addr["formatted"])
        user_sessions[user_id]["coordinates"].append(addr["coords"])
        if len(user_sessions[user_id]["coordinates"]) >= 2:
            coords = user_sessions[user_id]["coordinates"][-2:]
            body = {
                "coordinates": coords
            }
            headers = {
                "Authorization": ORS_API_KEY,
                "Content-Type": "application/json"
            }
            r = requests.post(DIRECTIONS_URL, json=body, headers=headers)
            dist = r.json()["features"][0]["properties"]["segments"][0]["distance"]
            dist_km = round(dist / 1000, 1)
            dist_rounded = round(dist_km / 10) * 10
        await update.message.reply_text(
        f"Добавлен маршрут: {user_sessions[user_id]['routes'][-2]} → {user_sessions[user_id]['routes'][-1]}\n"
        f"Расстояние: {dist_rounded} км ({dist_km} км точно)"
        )
        f"Добавлен маршрут: {user_sessions[user_id]['routes'][-2]} → {user_sessions[user_id]['routes'][-1]}\n"
        f"Расстояние: {dist_rounded} км ({dist_km} км точно)"
        return ROUTE_INPUT
async def get_odo_end(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = str(update.effective_user.id)
    user_sessions[user_id]["odo_end"] = update.message.text
    await update.message.reply_text("✅ Спасибо! Все маршруты записаны и пробег учтён.")
    return ConversationHandler.END
async def cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Операция отменена.")
    return ConversationHandler.END
if __name__ == "__main__":
    TOKEN = "8066885623:AAH4DKVqNfqx5OSRwT4LZL9Io_CzG2RgaqI"
    app = ApplicationBuilder().token(TOKEN).build()
    conv_handler = ConversationHandler(
        entry_points=[CommandHandler("start", start)],
        states={
            DRIVER: [MessageHandler(filters.TEXT & ~filters.COMMAND, driver)],
            CAR_MAKE: [MessageHandler(filters.TEXT & ~filters.COMMAND, car_make)],
            CAR_PLATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, car_plate)],
            DATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_date)],
            ODO_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_odo_start)],
            FUEL_NORM: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_fuel_norm)],
            FUEL_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_fuel_start)],
            ROUTE_INPUT: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_route_input)],
            ROUTE_CONFIRM: [MessageHandler(filters.TEXT & ~filters.COMMAND, confirm_route)],
            ODO_END: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_odo_end)],
        },
        fallbacks=[CommandHandler("cancel", cancel)],
    )
    app.add_handler(conv_handler)
    app.run_polling()