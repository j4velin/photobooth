import socket
import sys
import time
import RPi.GPIO as GPIO
import os
import fcntl
import struct
from functools import partial
from multiprocessing import Pool

PORT = 5555
GPIO_TRIGGER_PIN = 4
TAKE_PHOTO_CMD = "TAKE_PHOTO\n"
SCANNER_WORKERS = 10

def get_ip_address(ifname):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    return socket.inet_ntoa(fcntl.ioctl(
        s.fileno(),
        0x8915,  # SIOCGIFADDR
        struct.pack('256s', ifname[:15])
    )[20:24])

def test_port(prefix, suffix):
    host = prefix + str(suffix)
    print 'testing ' + str(host)
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(2)
    result = s.connect_ex((host, PORT))
    s.close()
    if result == 0:
        return host
    else:
        return False

def scan_hosts():
    prefix = get_ip_prefix()
    p = Pool(SCANNER_WORKERS)
    test_partial = partial(test_port, prefix)
    return filter(bool, p.map(test_partial, range(1,255)))

def get_ip_prefix():
    my_ip = get_ip_address('wlan0') 
    print 'my ip: ' + str(my_ip)
    return my_ip[:my_ip.rfind('.')]+'.'

result_ip = None

while result_ip == None:
    tablet = scan_hosts()
    print str(tablet)

    if len(tablet) > 0:
        result_ip = tablet[0]
    else:
        print 'no device found'
        time.sleep(10)

print "Result IP: " + result_ip

GPIO.setmode(GPIO.BCM)
GPIO.setup(GPIO_TRIGGER_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.settimeout(None)
s.connect((result_ip, PORT))
try:
    while True:
        input_state = GPIO.input(GPIO_TRIGGER_PIN)
        if input_state == False:
            # print "taking photo..."
            s.send(TAKE_PHOTO_CMD.encode()) 
            time.sleep(5)  
except KeyboardInterrupt:
    print "keyboard interrupt"
s.close()
