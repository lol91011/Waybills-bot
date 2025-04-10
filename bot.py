import logging
from telegram import Update, ReplyKeyboardMarkup, KeyboardButton
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ContextTypes, ConversationHandler
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
async def ask_name(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите ФИО водителя:")
    return ASK_NAME

async def ask_car(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите марку автомобиля:")
    return ASK_CAR

async def ask_start_odometer(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите начальный пробег:")
    return ASK_START_ODOMETER

async def ask_fuel_consumption(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите средний расход топлива:")
    return ASK_FUEL_CONSUMPTION

async def ask_fuel_before_trip(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите количество топлива до выезда:")
    return ASK_FUEL_BEFORE_TRIP

async def ask_route(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите маршрут:")
    return ASK_ROUTE

async def ask_end_odometer(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите конечный пробег:")
    return ASK_END_ODOMETER

async def confirmation(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Спасибо! Данные сохранены.")
    return ConversationHandler.END


async def ask_name(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите ФИО водителя:")
    return ASK_NAME
async def ask_car(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите марку автомобиля:")
    return ASK_CAR
async def ask_start_odometer(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите начальный пробег:")
    return ASK_START_ODOMETER
async def ask_fuel_consumption(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите средний расход топлива:")
    return ASK_FUEL_CONSUMPTION
async def ask_fuel_before_trip(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите количество топлива до выезда:")
    return ASK_FUEL_BEFORE_TRIP
async def ask_route(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите маршрут:")
    return ASK_ROUTE
async def ask_end_odometer(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите конечный пробег:")
    return ASK_END_ODOMETER
async def confirmation(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Спасибо! Данные сохранены.")
    return ConversationHandler.END
    await update.message.reply_text("Добро пожаловать! Введите дату или нажмите 'Сегодня'.")
    return ASK_DATE
import os
from collections import defaultdict

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
(ASK_DATE, ASK_NAME, ASK_CAR, ASK_START_ODOMETER, ASK_FUEL_CONSUMPTION,
 ASK_FUEL_BEFORE_TRIP, ASK_ROUTE, ASK_END_ODOMETER, CONFIRMATION) = range(9)

# Этапы разговора
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