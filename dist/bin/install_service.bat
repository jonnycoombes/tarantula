set SERVICE_NAME=OTFacade

REM Service log configuration
set PR_DISPLAYNAME=OTCS Facade
set PR_DESCRIPTION=OpenText BP Workspace Facade
set PR_LOGPREFIX=%SERVICE_NAME%
set PR_LOGPATH=C:\OPENTEXT\facade\logs
set PR_STDOUTPUT=auto
set PR_STDERROR=auto
set PR_LOGLEVEL=DEBUG

REM Path to java installation
set PR_JVM=C:\Program Files\Java\jre1.8.0_161\bin\server\jvm.dll
set PR_CLASSPATH=C:\OPENTEXT\facade\lib\*;

REM Startup configuration
set PR_STARTUP=auto
set PR_STARTMODE=jvm
set PR_STARTCLASS=play.core.server.ProdServerStart
set PR_STARTPARAMS=C:\OPENTEXT\facade\

REM Shutdown configuration
set PR_STOPMODE=jvm
set PR_STOPCLASS=play.core.server.ProdServerStart
set PR_STOPPARAMS=stop

REM JVM configuration
set PR_JVMMS=256
set PR_JVMMX=4096
set PR_JVMSS=4000

REM Install service
prunsrv.exe //IS//%SERVICE_NAME%