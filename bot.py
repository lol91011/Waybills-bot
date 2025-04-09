from telegram import Update
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ContextTypes, ConversationHandler

# Состояния
DATE, DRIVER, CAR_MAKE, CAR_PLATE, ODO_START, FUEL_NORM, FUEL_START, ROUTES, ODO_END = range(9)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Привет! Введи дату (ГГГГ-ММ-ДД):")
    return DATE

async def get_date(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["date"] = update.message.text
    await update.message.reply_text("Введите ФИО водителя:")
    return DRIVER

async def get_driver(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["driver"] = update.message.text
    await update.message.reply_text("Введите марку автомобиля:")
    return CAR_MAKE

async def get_car_make(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["car_make"] = update.message.text
    await update.message.reply_text("Введите госномер автомобиля:")
    return CAR_PLATE

async def get_car_plate(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["car_plate"] = update.message.text
    await update.message.reply_text("Введите начальный пробег (км):")
    return ODO_START

async def get_odo_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["odo_start"] = update.message.text
    await update.message.reply_text("Введите расход по норме (л/100 км):")
    return FUEL_NORM

async def get_fuel_norm(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["fuel_norm"] = update.message.text
    await update.message.reply_text("Введите остаток топлива до выезда (л):")
    return FUEL_START

async def get_fuel_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["fuel_start"] = update.message.text
    context.user_data["routes"] = []
    await update.message.reply_text("Вводи маршруты в формате: Откуда -> Куда. Напиши 'готово', когда закончишь.")
    return ROUTES

async def get_routes(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text.strip()
    if text.lower() == "готово":
        await update.message.reply_text("Введите конечный пробег (км):")
        return ODO_END
    if "->" in text:
        if "routes" not in context.user_data:
            context.user_data["routes"] = []
        context.user_data["routes"].append(text)
    else:
        await update.message.reply_text("Формат маршрута должен быть: Откуда -> Куда")
    return ROUTES

async def get_odo_end(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data["odo_end"] = update.message.text
    await update.message.reply_text("✅ Спасибо! Все данные получены. Генерирую путевой лист...")
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
            DATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_date)],
            DRIVER: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_driver)],
            CAR_MAKE: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_car_make)],
            CAR_PLATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_car_plate)],
            ODO_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_odo_start)],
            FUEL_NORM: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_fuel_norm)],
            FUEL_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_fuel_start)],
            ROUTES: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_routes)],
            ODO_END: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_odo_end)],
        },
        fallbacks=[CommandHandler("cancel", cancel)],
    )

    app.add_handler(conv_handler)
    app.run_polling()