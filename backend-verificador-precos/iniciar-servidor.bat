@echo off
title Verificador de Precos

cd /d "%~dp0"

echo ==========================================
echo      BACKEND - VERIFICADOR DE PRECOS
echo ==========================================
echo.
echo 1 - Servidor LOCAL
echo 2 - Servidor na REDE
echo.

set /p opcao=Escolha uma opcao: 

if "%opcao%"=="1" goto local
if "%opcao%"=="2" goto rede

echo.
echo Opcao invalida.
pause
exit /b

:local
echo.
echo Iniciando servidor local...
echo Endereco: http://127.0.0.1:8000/
echo.
".venv\Scripts\python.exe" manage.py runserver
goto fim

:rede
echo.
echo Iniciando servidor para acesso pela rede...
echo Porta: 8000
echo.
".venv\Scripts\python.exe" manage.py runserver 0.0.0.0:8000
goto fim

:fim
pause