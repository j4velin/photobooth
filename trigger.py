# python script to run on the RaspberryPi with the trigger button


import socket
import sys
import time
import RPi.GPIO as GPIO
import os

IP_PREFIX = '10.5.5.'
PORT = 5555
GPIO_TRIGGER_PIN = 4
TAKE_PHOTO_CMD = "TAKE_PHOTO\n"

current = 35
result_ip = None
print 'searching for Android device...'
while result_ip is None and current < 255:
    ip = IP_PREFIX + str(current)
    sys.stdout.write('testing ' + ip)
    sys.stdout.flush()
    sys.stdout.write('\r')

    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(2)
    result = s.connect_ex((ip, PORT))
    if result == 0:
        result_ip = ip
    else:
        current = current + 1

if result_ip is None:
    print "No device found"
    sys.exit(1)

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
            print "taking photo..."
            s.send(TAKE_PHOTO_CMD.encode()) 
            time.sleep(5)  
except KeyboardInterrupt:
    print "keyboard interrupt"
s.close ()
