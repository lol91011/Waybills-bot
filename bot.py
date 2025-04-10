import logging
from telegram import Update, ReplyKeyboardMarkup, KeyboardButton
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ContextTypes, ConversationHandler
import os
from collections import defaultdict

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Этапы разговора
(ASK_DATE, ASK_NAME, ASK_CAR, ASK_START_ODOMETER, ASK_FUEL_CONSUMPTION,
 ASK_FUEL_BEFORE_TRIP, ASK_ROUTE, ASK_END_ODOMETER, CONFIRMATION) = range(9)

# Простая база данных в памяти
user_data_store = defaultdict(dict)
frequent_addresses = defaultdict(lambda: { "Туркменская, 14а": 1 })

def get_date_keyboard():
    return ReplyKeyboardMarkup([[KeyboardButton("Сегодня")]], resize_keyboard=True)

def get_address_keyboard(user_id):
    addresses = sorted(frequent_addresses[user_id].items(), key=lambda x: -x[1])
    keyboard = [[KeyboardButton(addr[0])] for addr in addresses[:3]]
    return ReplyKeyboardMarkup(keyboard, resize_keyboard=True)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Выберите дату:", reply_markup=get_date_keyboard())
    return ASK_DATE

async def ask_name(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["date"] = update.message.text
    user_id = update.effective_user.id
    if "name" in user_data_store[user_id]:
        return await ask_car(update, context)
    await update.message.reply_text("Введите ФИО водителя:")
    return ASK_NAME

async def ask_car(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    if "name" not in user_data_store[user_id]:
        user_data_store[user_id]["name"] = update.message.text
    if "car" in user_data_store[user_id]:
        return await ask_start_odometer(update, context)
    await update.message.reply_text("Введите марку и госномер машины:")
    return ASK_CAR

async def ask_start_odometer(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    if "car" not in user_data_store[user_id]:
        user_data_store[user_id]["car"] = update.message.text
    await update.message.reply_text("Введите начальный пробег:")
    return ASK_START_ODOMETER

async def ask_fuel_consumption(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["start_odometer"] = update.message.text
    await update.message.reply_text("Введите средний расход топлива на 100 км:")
    return ASK_FUEL_CONSUMPTION

async def ask_fuel_before_trip(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["consumption"] = update.message.text
    await update.message.reply_text("Введите количество топлива до выезда:")
    return ASK_FUEL_BEFORE_TRIP

async def ask_route(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["fuel_before"] = update.message.text
    await update.message.reply_text("Введите маршрут:", reply_markup=get_address_keyboard(update.effective_user.id))
    return ASK_ROUTE

async def ask_end_odometer(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["route"] = update.message.text
    user_id = update.effective_user.id
    frequent_addresses[user_id][update.message.text] += 1
    await update.message.reply_text("Введите конечный пробег:")
    return ASK_END_ODOMETER

async def confirmation(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["end_odometer"] = update.message.text
    user_id = update.effective_user.id
    summary = (
        f"Дата: {context.user_data['date']}
"
        f"Водитель: {user_data_store[user_id]['name']}
"
        f"Машина: {user_data_store[user_id]['car']}
"
        f"Начальный пробег: {context.user_data['start_odometer']}
"
        f"Средний расход: {context.user_data['consumption']}
"
        f"Топливо до выезда: {context.user_data['fuel_before']}
"
        f"Маршрут: {context.user_data['route']}
"
        f"Конечный пробег: {context.user_data['end_odometer']}"
    )
    await update.message.reply_text("Проверьте данные:
" + summary)
    return ConversationHandler.END

def main():
    token = os.getenv("BOT_TOKEN")
    app = ApplicationBuilder().token(token).build()

    conv_handler = ConversationHandler(
        entry_points=[CommandHandler("start", start)],
        states={
            ASK_DATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_name)],
            ASK_NAME: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_car)],
            ASK_CAR: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_start_odometer)],
            ASK_START_ODOMETER: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_fuel_consumption)],
            ASK_FUEL_CONSUMPTION: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_fuel_before_trip)],
            ASK_FUEL_BEFORE_TRIP: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_route)],
            ASK_ROUTE: [MessageHandler(filters.TEXT & ~filters.COMMAND, ask_end_odometer)],
            ASK_END_ODOMETER: [MessageHandler(filters.TEXT & ~filters.COMMAND, confirmation)],
        },
        fallbacks=[]
    )

    app.add_handler(conv_handler)
    app.run_polling()

if __name__ == "__main__":
    main()