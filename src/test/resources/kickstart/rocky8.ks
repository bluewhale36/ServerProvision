#version=RHEL8
url --url=http://pxe.example/api/pxe/v1/rhel/TOKEN/tree
text
lang en_US.UTF-8
keyboard --xlayouts='us'
network --bootproto=dhcp --activate
firstboot --disable
timezone Asia/Seoul
ignoredisk --only-use=disk/by-id/wwn-0x5000c500a1b2c3d4
zerombr
clearpart --all --initlabel --drives=disk/by-id/wwn-0x5000c500a1b2c3d4
part /boot/efi --fstype=efi --size=600 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part /boot --fstype=xfs --size=1024 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part swap --fstype=swap --size=8192 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part / --fstype=xfs --size=1 --grow --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part /var --fstype=xfs --size=51200 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
bootloader
rootpw --lock
user --name=admin --groups=wheel --iscrypted --password=$6$exUserSalt$exampleUserCryptNotComputed
user --name=viewer --lock
reboot

%packages
@^minimal-environment
%end

%addon com_redhat_kdump --disable
%end
