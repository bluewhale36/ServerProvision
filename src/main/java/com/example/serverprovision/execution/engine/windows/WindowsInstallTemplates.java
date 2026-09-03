package com.example.serverprovision.execution.engine.windows;

/**
 * 실측 1~3호가 실기에서 성공시킨 파일 셋의 원문(E4-1-a-3 §1-8) — 구조는 바꾸지 않고 {@code __KEY__} 자리만 채운다.
 * 배치는 내장 명령만 쓴다(Setup PE 에는 where · findstr · curl 이 없다) · 전부 ASCII(코드페이지 949 가 UTF-8 을 깨뜨린다).
 * FirstLogonCommands 는 표식 파일(1) + 완료 보고 스크립트 실행(2 — E4-1-a-4, 스크립트 본체는 $OEM$ 로 설치된 OS 에 들어간다).
 */
public final class WindowsInstallTemplates {

    private WindowsInstallTemplates() {
    }

    public static final String WINPESHL_INI = """
            [LaunchApps]
            cmd.exe, /k X:\\Windows\\System32\\install.bat
            """;

    public static final String INSTALL_BAT = """
            @echo off
            rem ServerProvision - Windows Server 2025 unattended install (WinPE stage, rendered per guest).
            rem Setup PE (boot.wim index 2) has NO where.exe / findstr.exe - internal commands only + ipconfig / net / diskpart / setup.
            rem ASCII only (codepage 949 garbles UTF-8).
            wpeinit
            echo [ServerProvision] wpeinit done. Waiting for network...

            set /a n=0
            :wait
            ping -n 2 __SHARE_HOST__ >nul 2>&1 && goto ok
            set /a n+=1
            if %n% GEQ 30 goto fail
            goto wait
            :ok

            echo [ServerProvision] ipconfig:
            ipconfig

            echo [ServerProvision] diskpart list disk:
            echo list disk> X:\\dp.txt
            echo exit>> X:\\dp.txt
            diskpart /s X:\\dp.txt

            rem The SMB client (LanmanWorkstation) binds to adapters present at its start - error 53 for the first minutes after boot.
            rem Wait for network, restart the workstation service, then retry up to 5 min with timestamps (fieldwork #3: 62 s after network).
            wpeutil WaitForNetwork
            net stop LanmanWorkstation /y >nul 2>&1
            net start LanmanWorkstation >nul 2>&1
            set /a m=0
            :smb
            set /a m+=1
            echo [ServerProvision] net use attempt %m% at %TIME% to __SHARE_UNC__
            net use N: __SHARE_UNC__ /user:__DEPLOY_USER__ "__DEPLOY_PASSWORD__" && goto mounted
            if %m% GEQ 20 goto fail
            ping -n 16 127.0.0.1 >nul
            goto smb
            :mounted
            echo [ServerProvision] share mounted at %TIME% (attempt %m%)

            echo [ServerProvision] Starting setup /unattend at %TIME% ...
            N:\\sources\\setup.exe /unattend:X:\\Windows\\System32\\autounattend.xml
            echo [ServerProvision] setup.exe returned %ERRORLEVEL% at %TIME% - console stays open. Panther error log:
            if exist X:\\Windows\\Panther\\setuperr.log type X:\\Windows\\Panther\\setuperr.log
            goto :eof

            :fail
            echo [ServerProvision] FAILED - install source not reachable. Panther log: X:\\Windows\\Panther\\setupact.log
            """;

    public static final String AUTOUNATTEND_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- ServerProvision E4-1-a-3 - rendered per guest. Structure = fieldwork #1~#3 (2026-09-02). DiskID 0 / single disk until E4-1-a-6. -->
            <unattend xmlns="urn:schemas-microsoft-com:unattend">

              <settings pass="windowsPE">
                <component name="Microsoft-Windows-International-Core-WinPE" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
                  <SetupUILanguage><UILanguage>__UI_LANGUAGE__</UILanguage></SetupUILanguage>
                  <InputLocale>__INPUT_LOCALE__</InputLocale>
                  <SystemLocale>__UI_LANGUAGE__</SystemLocale>
                  <UILanguage>__UI_LANGUAGE__</UILanguage>
                  <UserLocale>__UI_LANGUAGE__</UserLocale>
                </component>
                <component name="Microsoft-Windows-Setup" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
                  <UserData>
                    <AcceptEula>true</AcceptEula>
                    <FullName>ServerProvision</FullName>
                    <Organization>ServerProvision</Organization>
                    <ProductKey><Key>__PRODUCT_KEY__</Key><WillShowUI>OnError</WillShowUI></ProductKey>
                  </UserData>
                  <DiskConfiguration>
                    <WillShowUI>OnError</WillShowUI>
                    <Disk wcm:action="add" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">
                      <DiskID>0</DiskID>
                      <WillWipeDisk>true</WillWipeDisk>
                      <CreatePartitions>
                        <CreatePartition wcm:action="add"><Order>1</Order><Type>EFI</Type><Size>260</Size></CreatePartition>
                        <CreatePartition wcm:action="add"><Order>2</Order><Type>MSR</Type><Size>128</Size></CreatePartition>
                        <CreatePartition wcm:action="add"><Order>3</Order><Type>Primary</Type><Extend>true</Extend></CreatePartition>
                      </CreatePartitions>
                      <ModifyPartitions>
                        <ModifyPartition wcm:action="add"><Order>1</Order><PartitionID>1</PartitionID><Format>FAT32</Format><Label>System</Label></ModifyPartition>
                        <ModifyPartition wcm:action="add"><Order>2</Order><PartitionID>2</PartitionID></ModifyPartition>
                        <ModifyPartition wcm:action="add"><Order>3</Order><PartitionID>3</PartitionID><Format>NTFS</Format><Label>Windows</Label><Letter>C</Letter></ModifyPartition>
                      </ModifyPartitions>
                    </Disk>
                  </DiskConfiguration>
                  <ImageInstall>
                    <OSImage>
                      <InstallFrom>
                        <MetaData wcm:action="add" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">
                          <Key>/IMAGE/NAME</Key>
                          <Value>__IMAGE_NAME__</Value>
                        </MetaData>
                      </InstallFrom>
                      <InstallTo><DiskID>0</DiskID><PartitionID>3</PartitionID></InstallTo>
                      <WillShowUI>OnError</WillShowUI>
                    </OSImage>
                  </ImageInstall>
                </component>
              </settings>

              <settings pass="specialize">
                <component name="Microsoft-Windows-Shell-Setup" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
                  <ComputerName>__COMPUTER_NAME__</ComputerName>
                  <TimeZone>__TIME_ZONE__</TimeZone>
                </component>
              </settings>

              <settings pass="oobeSystem">
                <component name="Microsoft-Windows-Shell-Setup" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
                  <UserAccounts>
                    <AdministratorPassword>
                      <Value>__ADMIN_PASSWORD_B64__</Value>
                      <PlainText>false</PlainText>
                    </AdministratorPassword>
                  </UserAccounts>
                  <AutoLogon>
                    <Enabled>true</Enabled>
                    <LogonCount>1</LogonCount>
                    <Username>Administrator</Username>
                    <Password>
                      <Value>__AUTOLOGON_PASSWORD_B64__</Value>
                      <PlainText>false</PlainText>
                    </Password>
                  </AutoLogon>
                  <OOBE>
                    <HideEULAPage>true</HideEULAPage>
                    <HideLocalAccountScreen>true</HideLocalAccountScreen>
                    <HideOEMRegistrationScreen>true</HideOEMRegistrationScreen>
                    <HideOnlineAccountScreens>true</HideOnlineAccountScreens>
                    <HideWirelessSetupInOOBE>true</HideWirelessSetupInOOBE>
                    <ProtectYourPC>3</ProtectYourPC>
                    <SkipMachineOOBE>true</SkipMachineOOBE>
                    <SkipUserOOBE>true</SkipUserOOBE>
                  </OOBE>
                  <FirstLogonCommands>
                    <SynchronousCommand wcm:action="add" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">
                      <Order>1</Order>
                      <CommandLine>cmd /c echo %DATE% %TIME% ServerProvision &gt; C:\\spv-firstlogon.txt</CommandLine>
                      <Description>ServerProvision first logon marker</Description>
                    </SynchronousCommand>
                    <SynchronousCommand wcm:action="add" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">
                      <Order>2</Order>
                      <CommandLine>powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\\SPV\\spv-report.ps1 -BaseUrl "__REPORT_BASE_URL__" -Token "__GUEST_TOKEN__"</CommandLine>
                      <Description>ServerProvision completion report</Description>
                    </SynchronousCommand>
                  </FirstLogonCommands>
                </component>
                <component name="Microsoft-Windows-International-Core" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
                  <InputLocale>__INPUT_LOCALE__</InputLocale>
                  <SystemLocale>__UI_LANGUAGE__</SystemLocale>
                  <UILanguage>__UI_LANGUAGE__</UILanguage>
                  <UserLocale>__UI_LANGUAGE__</UserLocale>
                </component>
              </settings>
            </unattend>
            """;
}
