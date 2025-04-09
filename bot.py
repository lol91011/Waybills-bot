
import logging
from telegram import Update, InputFile
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ContextTypes, ConversationHandler
from openpyxl import load_workbook
from openpyxl.worksheet.properties import WorksheetProperties, PageSetupProperties
import datetime

# --- Константы состояний ---
DATE, DRIVER, CAR_MAKE, CAR_PLATE, ROUTES, ODO_START, ODO_END, FUEL_START, FUEL_END, FUEL_NORM = range(11)

# --- Сессии пользователей ---
sessions = {}
TEMPLATE_PATH = "Шаблон_Путевой_лист_Форма3.xlsx"

# --- Генерация Excel-файла на основе шаблона ---
def generate_trip_excel(user_data, output_path):
    wb = load_workbook(TEMPLATE_PATH)
    ws1 = wb["стр1"]
    ws2 = wb["стр2"]

    ws1["M12"] = user_data["driver"]
    ws1["V10"] = user_data["car_make"]
    ws1["AL11"] = user_data["car_plate"]
    ws1["BU19"] = user_data["odo_start"]
    ws1["BT45"] = user_data["odo_end"]
    ws1["BT37"] = user_data["fuel_start"]
    ws1["BT38"] = user_data["fuel_end"]
    ws1["BT39"] = user_data["fuel_norm"]

    for i, route in enumerate(user_data["routes"]):
        row = 5 + i
        ws2[f"E{row}"] = route["from"]
        ws2[f"H{row}"] = route["to"]
        ws2[f"O{row}"] = route["distance_rounded"]

    ws2.sheet_properties = WorksheetProperties(pageSetUpPr=PageSetupProperties(fitToPage=True))
    ws2.page_setup.fitToWidth = 1
    ws2.page_setup.fitToHeight = 1
    ws2.page_setup.paperSize = 9
    ws2.page_setup.orientation = "portrait"
    ws2.column_dimensions["E"].width = 30
    ws2.column_dimensions["H"].width = 30
    ws2.column_dimensions["O"].width = 8

    wb.save(output_path)

# --- Хендлеры Telegram ---
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("Введите дату (ДД.ММ.ГГГГ):")
    return DATE

async def get_date(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.message.from_user.id
    try:
        sessions[user_id] = {"date": datetime.datetime.strptime(update.message.text, "%d.%m.%Y").date()}
        await update.message.reply_text("Введите ФИО водителя:")
        return DRIVER
    except:
        await update.message.reply_text("Неверный формат даты. Попробуйте снова:")
        return DATE

async def get_driver(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[update.message.from_user.id]["driver"] = update.message.text.strip()
    await update.message.reply_text("Введите марку автомобиля:")
    return CAR_MAKE

async def get_car_make(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[update.message.from_user.id]["car_make"] = update.message.text.strip()
    await update.message.reply_text("Введите гос. номер:")
    return CAR_PLATE

async def get_car_plate(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[update.message.from_user.id]["car_plate"] = update.message.text.strip()
    sessions[update.message.from_user.id]["routes"] = []
    await update.message.reply_text("Введите маршрут (Откуда -> Куда). Напишите 'готово', когда закончите:")
    return ROUTES

async def get_routes(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text.strip()
    user_id = update.message.from_user.id
    if text.lower() == "готово":
        await update.message.reply_text("Введите пробег на начало (км):")
        return ODO_START
    elif "->" in text:
        parts = text.split("->")
        sessions[user_id]["routes"].append({
            "from": parts[0].strip(),
            "to": parts[1].strip(),
            "distance_rounded": 10  # можно подключить API позже
        })
        return ROUTES
    else:
        await update.message.reply_text("Неверный формат. Введите в виде 'Откуда -> Куда':")
        return ROUTES

async def get_odo_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[update.message.from_user.id]["odo_start"] = int(update.message.text.strip())
    await update.message.reply_text("Введите пробег на конец (км):")
    return ODO_END

async def get_odo_end(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[update.message.from_user.id]["odo_end"] = int(update.message.text.strip())
    await update.message.reply_text("Введите топливо на начало (л):")
    return FUEL_START

async def get_fuel_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[update.message.from_user.id]["fuel_start"] = float(update.message.text.strip())
    await update.message.reply_text("Введите топливо на конец (л):")
    return FUEL_END

async def get_fuel_end(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sessions[update.message.from_user.id]["fuel_end"] = float(update.message.text.strip())
    await update.message.reply_text("Введите расход по норме (л):")
    return FUEL_NORM

async def get_fuel_norm(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.message.from_user.id
    sessions[user_id]["fuel_norm"] = float(update.message.text.strip())

    date_str = sessions[user_id]["date"].strftime("%d.%m.%Y")
    filename = f"Путевой_лист_{date_str}.xlsx"
    generate_trip_excel(sessions[user_id], filename)

    await update.message.reply_document(InputFile(filename), caption=f"Путевой лист за {date_str}")
    return ConversationHandler.END

def main():
    app = ApplicationBuilder().token("BOT_TOKEN").build()

    conv_handler = ConversationHandler(
        entry_points=[CommandHandler("start", start)],
        states={
            DATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_date)],
            DRIVER: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_driver)],
            CAR_MAKE: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_car_make)],
            CAR_PLATE: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_car_plate)],
            ROUTES: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_routes)],
            ODO_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_odo_start)],
            ODO_END: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_odo_end)],
            FUEL_START: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_fuel_start)],
            FUEL_END: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_fuel_end)],
            FUEL_NORM: [MessageHandler(filters.TEXT & ~filters.COMMAND, get_fuel_norm)],
        },
        fallbacks=[]
    )

    app.add_handler(conv_handler)
    app.run_polling()

if __name__ == "__main__":
    main()
