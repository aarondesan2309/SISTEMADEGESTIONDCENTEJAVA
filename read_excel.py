import pandas as pd
import sys

filename = r"C:\Users\aaron\Downloads\Recontratación mar-ago26 para contratos todos (Autoguardado).xlsm"
try:
    xl = pd.ExcelFile(filename)
    print("Sheets:", xl.sheet_names)
    # Buscamos la hoja de presupuesto
    for sheet in xl.sheet_names:
        if "presupuesto" in sheet.lower() or "ppt" in sheet.lower():
            df = xl.parse(sheet)
            print("--- Hoja:", sheet, "---")
            print(df.head(20).to_string())
except Exception as e:
    print("Error:", e)
