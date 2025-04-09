from telegram import Update
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ContextTypes, ConversationHandler
import datetime

# Состояния
DATE, DRIVER, CAR_MAKE, CAR_PLATE, ROUTES, ODO_START, ODO_END, FUEL_START, FUEL_END, FUEL_NORM = range(10)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Привет! Я помогу тебе сформировать путевой лист. Введи дату (ГГГГ-ММ-ДД):")
    return DATE

async def help_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "Команды:
"
        "/start — начать создание путевого листа
"
        "/help — помощь
"
        "/report — сформировать отчёт
"
        "/history — путевые листы за период
"
        "/cancel — отмена действия"
    )

async def date(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["date"] = update.message.text
    await update.message.reply_text("Введите ФИО водителя:")
    return DRIVER

async def driver(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["driver"] = update.message.text
    await update.message.reply_text("Введите марку автомобиля:")
    return CAR_MAKE

async def car_make(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["car_make"] = update.message.text
    await update.message.reply_text("Введите госномер автомобиля:")
    return CAR_PLATE

async def car_plate(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["car_plate"] = update.message.text
    await update.message.reply_text("Введите маршрут (откуда — куда):")
    return ROUTES

async def routes(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["routes"] = update.message.text
    await update.message.reply_text("Введите начальный пробег:")
    return ODO_START

async def odo_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["odo_start"] = update.message.text
    await update.message.reply_text("Введите конечный пробег:")
    return ODO_END

async def odo_end(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["odo_end"] = update.message.text
    await update.message.reply_text("Введите остаток топлива при выезде:")
    return FUEL_START

async def fuel_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["fuel_start"] = update.message.text
    await update.message.reply_text("Введите остаток топлива при возвращении:")
    return FUEL_END

async def fuel_end(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["fuel_end"] = update.message.text
    await update.message.reply_text("Введите расход по норме (л/100км):")
    return FUEL_NORM

async def fuel_norm(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["fuel_norm"] = update.message.text
    await update.message.reply_text("Путевой лист сформирован. Спасибо!")
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
            DATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, date)],
            DRIVER: [MessageHandler(filters.TEXT & ~filters.COMMAND, driver)],
            CAR_MAKE: [MessageHandler(filters.TEXT & ~filters.COMMAND, car_make)],
            CAR_PLATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, car_plate)],
            ROUTES: [MessageHandler(filters.TEXT & ~filters.COMMAND, routes)],
            ODO_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, odo_start)],
            ODO_END: [MessageHandler(filters.TEXT & ~filters.COMMAND, odo_end)],
            FUEL_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, fuel_start)],
            FUEL_END: [MessageHandler(filters.TEXT & ~filters.COMMAND, fuel_end)],
            FUEL_NORM: [MessageHandler(filters.TEXT & ~filters.COMMAND, fuel_norm)],
        },
        fallbacks=[CommandHandler("cancel", cancel)],
    )

    app.add_handler(conv_handler)
    app.add_handler(CommandHandler("help", help_command))

    app.run_polling()