#version=RHEL9
url --url=http://pxe.example/api/pxe/v1/rhel/TOKEN/tree
text
lang en_US.UTF-8
keyboard --xlayouts='us'
network --bootproto=dhcp --activate
firstboot --disable
timezone Asia/Seoul --utc
ignoredisk --only-use=disk/by-id/wwn-0x5000c500a1b2c3d4
zerombr
clearpart --all --initlabel --drives=disk/by-id/wwn-0x5000c500a1b2c3d4
part /boot/efi --fstype=efi --size=1024 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part /boot --fstype=ext4 --size=1024 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part swap --fstype=swap --size=16384 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part / --fstype=ext4 --size=1 --grow --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
bootloader
rootpw --iscrypted $6$exRootSalt$exampleRootCryptNotComputed
user --name=admin --groups=wheel --iscrypted --password=$6$exUserSalt$exampleUserCryptNotComputed
reboot

%packages
@^server-product-environment
@development
%end

%addon com_redhat_kdump --enable --reserve-mb='auto'
%end
