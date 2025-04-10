import logging
from telegram import Update, ReplyKeyboardMarkup, KeyboardButton
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ContextTypes, ConversationHandler
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Добро пожаловать! Введите дату или нажмите 'Сегодня'.")
    return ASK_DATE
import os
from collections import defaultdict

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

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