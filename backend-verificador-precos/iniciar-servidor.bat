@echo off
setlocal
title Verificador de Precos

cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
    echo ERRO: Python do ambiente virtual nao encontrado.
    echo Confira a pasta .venv deste projeto.
    pause
    exit /b 1
)

if not exist "iniciar_servidor.py" (
    echo ERRO: O arquivo iniciar_servidor.py nao foi encontrado.
    pause
    exit /b 1
)

".venv\Scripts\python.exe" -B iniciar_servidor.py
if not errorlevel 1 exit /b 0

echo.
echo Nao foi possivel concluir a inicializacao. Confira a mensagem acima.
pause
exit /b 1
