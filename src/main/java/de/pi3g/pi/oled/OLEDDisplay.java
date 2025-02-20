/*
 * Copyright (c) 2016, Florian Frankenberger
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * * Neither the name of the copyright holder nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package de.pi3g.pi.oled;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

/**
 * A raspberry pi driver for the 128x64 pixel OLED display (i2c bus).
 * The supported kind of display uses the SSD1306 driver chip and
 * is connected to the raspberry's i2c bus (bus 1).
 * <p>
 * Note that you need to enable i2c (using for example raspi-config).
 * Also note that you need to load the following kernel modules:
 * </p>
 * <pre>i2c-bcm2708</pre> and <pre>i2c_dev</pre>
 * <p>
 * Also note that it is possible to speed up the refresh rate of the
 * display up to ~60fps by adding the following to the config.txt of
 * your raspberry: dtparam=i2c1_baudrate=1000000
 * </p>
 * <p>
 * Sample usage:
 * </p>
 * <pre>
 * OLEDDisplay display = new OLEDDisplay();
 * display.drawStringCentered("Hello World!", 25, true);
 * display.update();
 * Thread.sleep(10000); //sleep some time, because the display
 *                      //is automatically cleared the moment
 *                      //the application terminates
 * </pre>
 * <p>
 * This class is basically a rough port of Adafruit's BSD licensed
 * SSD1306 library (https://github.com/adafruit/Adafruit_SSD1306)
 * </p>
 *
 * @author Florian Frankenberger
 */
public class OLEDDisplay {


    /**
     * Used to specify the orientation of a display.
     * This allows mounting the oled display upright or flipping it.
     * <p>
     * All rotation values are for clockwise rotation.
     * </p>
     */
    public enum Rotation {
        DEG_0,
        DEG_90,
        DEG_180,
        DEG_270
    }


    private static final Logger logger = LoggerFactory.getLogger(OLEDDisplay.class);

    private static final int DEFAULT_I2C_BUS = 1;
    private static final int DEFAULT_DISPLAY_ADDRESS = 0x3C;

    public static final int DISPLAY_WIDTH = 128;
//    public static final int DISPLAY_WIDTH_SH1106 = 132;
    public static final int DISPLAY_HEIGHT = 64;
    private static final int MAX_INDEX = (DISPLAY_HEIGHT / 8) * DISPLAY_WIDTH;

    private static final byte SSD1306_SETCONTRAST = (byte) 0x81;
    private static final byte SSD1306_DISPLAYALLON_RESUME = (byte) 0xA4;
    private static final byte SSD1306_DISPLAYALLON = (byte) 0xA5;
    private static final byte SSD1306_NORMALDISPLAY = (byte) 0xA6;
    private static final byte SSD1306_INVERTDISPLAY = (byte) 0xA7;
    private static final byte SSD1306_DISPLAYOFF = (byte) 0xAE;
    private static final byte SSD1306_DISPLAYON = (byte) 0xAF;

    private static final byte SSD1306_SETDISPLAYOFFSET = (byte) 0xD3;
    private static final byte SSD1306_SETCOMPINS = (byte) 0xDA;

    private static final byte SSD1306_SETVCOMDETECT = (byte) 0xDB;

    private static final byte SSD1306_SETDISPLAYCLOCKDIV = (byte) 0xD5;
    private static final byte SSD1306_SETPRECHARGE = (byte) 0xD9;

    private static final byte SSD1306_SETMULTIPLEX = (byte) 0xA8;

    private static final byte SSD1306_SETLOWCOLUMN = (byte) 0x00;
    private static final byte SSD1306_SETHIGHCOLUMN = (byte) 0x10;

    private static final byte SSD1306_SETSTARTLINE = (byte) 0x40;

    private static final byte SSD1306_MEMORYMODE = (byte) 0x20;
    private static final byte SSD1306_COLUMNADDR = (byte) 0x21;
    private static final byte SSD1306_PAGEADDR = (byte) 0x22;

    private static final byte SSD1306_COMSCANINC = (byte) 0xC0;
    private static final byte SSD1306_COMSCANDEC = (byte) 0xC8;

    private static final byte SSD1306_SEGREMAP = (byte) 0xA0;

    private static final byte SSD1306_CHARGEPUMP = (byte) 0x8D;

    private static final byte SSD1306_EXTERNALVCC = (byte) 0x1;
    private static final byte SSD1306_SWITCHCAPVCC = (byte) 0x2;

    private final I2C device;
    private final Rotation rotation;

    private Context pi4j; 
	private I2CProvider i2CProvider;
	private I2CConfig i2cConfig;
	
    private final byte[] imageBuffer = new byte[(DISPLAY_WIDTH * DISPLAY_HEIGHT) / 8];

	private boolean usePageAdressMode;

    /**
     * creates an OLED display object with default
     * i2c bus 1, default display address of 0x3C and
     * and the default orientation
     *
     * @throws IOException
     * @throws com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException
     */
    public OLEDDisplay() throws IOException {
        this(DEFAULT_I2C_BUS, DEFAULT_DISPLAY_ADDRESS, Rotation.DEG_0, false);
    }

    /**
     * creates an OLED display object with default
     * i2c bus 1, the given display address and the default orientation
     *
     * @param displayAddress the i2c bus address of the display
     * @throws IOException
     * @throws com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException
     */
    public OLEDDisplay(int displayAddress, boolean usePageAdressMode) throws IOException {
        this(DEFAULT_I2C_BUS, displayAddress, Rotation.DEG_0, usePageAdressMode);
    }

    /**
     * creates an OLED display object with the given
     * i2c bus 1, the given display address and the default orientation
     *
     * @param busNumber      the i2c bus number (use constants from I2CBus)
     * @param displayAddress the i2c bus address of the display
     * @throws IOException
     * @throws com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException
     */
    public OLEDDisplay(int busNumber, int displayAddress, boolean usePageAdressMode) throws IOException {
        this(busNumber, displayAddress, Rotation.DEG_0, usePageAdressMode);
    }

    /**
     * creates an OLED display object with default
     * i2c bus 1, default display address of 0x3C
     * and a given rotation
     *
     * @param rotation orientation of the display, can be used if the display is mounted upright or flipped
     * @throws IOException
     * @throws com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException
     */
    public OLEDDisplay(Rotation rotation, boolean usePageAdressMode) throws IOException {
        this(DEFAULT_I2C_BUS, DEFAULT_DISPLAY_ADDRESS, rotation, usePageAdressMode);
    }

    /**
     * creates an OLED display object with default
     * i2c bus 1, given display address and a given rotation
     *
     * @param displayAddress the i2c bus address of the display
     * @param rotation orientation of the display, can be used if the display is mounted upright or flipped
     * @throws IOException
     * @throws com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException
     */
    public OLEDDisplay(int displayAddress, Rotation rotation, boolean usePageAdressMode) throws IOException {
        this(DEFAULT_I2C_BUS, displayAddress, rotation, usePageAdressMode);
    }

    /**
     * constructor with all parameters
     *
     * @param busNumber the i2c bus number (use constants from I2CBus)
     * @param displayAddress the i2c bus address of the display
     * @param rotation orientation of the display, can be used if the display is mounted upright or flipped
     * @param usePageAdressMode use addressing for SH1106-compatible displays, cheap ebay ones.
     * @throws IOException
     * @throws com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException
     */
    public OLEDDisplay(int busNumber, int displayAddress, Rotation rotation, boolean usePageAdressMode) throws IOException {
		pi4j = Pi4J.newAutoContext();
		i2CProvider = pi4j.provider("linuxfs-i2c");
		i2cConfig = I2C.newConfigBuilder(pi4j).id("TCA9534").bus(busNumber).device(displayAddress).build();
		device = i2CProvider.create(i2cConfig);
		
        this.rotation = rotation;
        this.usePageAdressMode = usePageAdressMode;

        logger.info("Opened i2c bus");

        clear();

        //add shutdown hook that clears the display
        //and closes the bus correctly when the software
        //if terminated.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutdown();
            }
        });

        init();
    }

    public synchronized void clear() {
        Arrays.fill(imageBuffer, (byte) 0x00);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public int getWidth() {
        switch (rotation) {
            case DEG_90:
            case DEG_270:
                return DISPLAY_HEIGHT;
            case DEG_0:
            case DEG_180:
            default:
                return DISPLAY_WIDTH;
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public int getHeight() {
        switch (rotation) {
            case DEG_90:
            case DEG_270:
                return DISPLAY_WIDTH;
            case DEG_0:
            case DEG_180:
            default:
                return DISPLAY_HEIGHT;
        }
    }

    private void writeCommand(byte command) throws IOException {
        device.writeRegister(0x00, command);
    }

    private void init() throws IOException {
        writeCommand(SSD1306_DISPLAYOFF);                    // 0xAE
        writeCommand(SSD1306_SETDISPLAYCLOCKDIV);            // 0xD5
        writeCommand((byte) 0x80);                           // the suggested ratio 0x80
        writeCommand(SSD1306_SETMULTIPLEX);                  // 0xA8
        writeCommand((byte) 0x3F);
        writeCommand(SSD1306_SETDISPLAYOFFSET);              // 0xD3
        writeCommand((byte) 0x0);                            // no offset
        writeCommand((byte) (SSD1306_SETSTARTLINE | 0x0));   // line #0
        writeCommand(SSD1306_CHARGEPUMP);                    // 0x8D
        writeCommand((byte) 0x14);
        writeCommand(SSD1306_MEMORYMODE);                    // 0x20
        if (usePageAdressMode) {
        	writeCommand((byte) 0x02); 						 //page adressing mode
        } else {
        	writeCommand((byte) 0x00);                       //horizontal addressing mode, 0x0 act like ks0108.
        }
        writeCommand((byte) (SSD1306_SEGREMAP | 0x1));
        writeCommand(SSD1306_COMSCANDEC);
        writeCommand(SSD1306_SETCOMPINS);                    // 0xDA
        writeCommand((byte) 0x12);
        writeCommand(SSD1306_SETCONTRAST);                   // 0x81
        writeCommand((byte) 0xCF);
        writeCommand(SSD1306_SETPRECHARGE);                  // 0xd9
        writeCommand((byte) 0xF1);
        writeCommand(SSD1306_SETVCOMDETECT);                 // 0xDB
        writeCommand((byte) 0x40);
        writeCommand(SSD1306_DISPLAYALLON_RESUME);           // 0xA4
        writeCommand(SSD1306_NORMALDISPLAY);

        writeCommand(SSD1306_DISPLAYON);//--turn on oled panel
    }

    
    @SuppressWarnings("SuspiciousNameCombination")
    public synchronized void setPixel(int x, int y, boolean on) {
        switch (rotation) {
            default:
            case DEG_0:
                updateImageBuffer(x, y, on);
                break;
            case DEG_90:
                updateImageBuffer(y, getWidth() - x - 1, on);
                break;
            case DEG_180:
                updateImageBuffer(getWidth() - x - 1, getHeight() - y - 1, on);
                break;
            case DEG_270:
                updateImageBuffer(getHeight() - y - 1, x, on);
                break;
        }
    }

    private synchronized void updateImageBuffer(int x, int y, boolean on) {
        final int pos = x + (y / 8) * DISPLAY_WIDTH;
        if (pos >= 0 && pos < MAX_INDEX) {
            if (on) {
                this.imageBuffer[pos] |= (1 << (y & 0x07));
            } else {
                this.imageBuffer[pos] &= ~(1 << (y & 0x07));
            }
        }
    }

    public synchronized void drawChar(char c, Font font, int x, int y, boolean on) {
        font.drawChar(this, c, x, y, on);
    }

    public synchronized void drawString(String string, Font font, int x, int y, boolean on) {
        int posX = x;
        int posY = y;
        for (char c : string.toCharArray()) {
            if (c == '\n') {
                posY += font.getOuterHeight();
                posX = x;
            } else {
                if (posX >= 0 && posX + font.getWidth() < this.getWidth()
                        && posY >= 0 && posY + font.getHeight() < this.getHeight()) {
                    drawChar(c, font, posX, posY, on);
                }
                posX += font.getOuterWidth();
            }
        }
    }

    public synchronized void drawStringCentered(String string, Font font, int y, boolean on) {
        final int strSizeX = string.length() * font.getOuterWidth();
        final int x = (this.getWidth() - strSizeX) / 2;
        drawString(string, font, x, y, on);
    }

    public synchronized void clearRect(int x, int y, int width, int height, boolean on) {
        for (int posX = x; posX < x + width; ++posX) {
            for (int posY = y; posY < y + height; ++posY) {
                setPixel(posX, posY, on);
            }
        }
    }

    /**
     * draws the given image over the current image buffer. The image
     * is automatically converted to a binary image (if it not already
     * is).
     * <p>
     * Note that the current buffer is not cleared before, so if you
     * want the image to completely overwrite the current display
     * content you need to call clear() before.
     * </p>
     *
     * @param image
     * @param x
     * @param y
     */
    public synchronized void drawImage(BufferedImage image, int x, int y) {
        BufferedImage tmpImage = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        tmpImage.getGraphics().drawImage(image, x, y, null);

        int index = 0;
        int pixelval;
        final byte[] pixels = ((DataBufferByte) tmpImage.getRaster().getDataBuffer()).getData();
        for (int posY = 0; posY < DISPLAY_HEIGHT; posY++) {
            for (int posX = 0; posX < DISPLAY_WIDTH / 8; posX++) {
                for (int bit = 0; bit < 8; bit++) {
                    pixelval = (byte) ((pixels[index/8] >>  (7 - bit)) & 0x01);
                    setPixel(posX * 8 + bit, posY, pixelval > 0);
                    index++;
                }
            }
        }
    }

    /**
     * 
     * taken from SH1106, see here for example: https://github.com/feng1126/SH1106
     * also read about page adressing mode here:
     * https://hw101.tbs1.de/ssd1306/doc/ssd1306_datasheet.pdf
     * 
     * from what i could find, the SH1106 line of displays only support 
     * page adressing mode.
     * 
     * @throws IOException
     */
    public synchronized void update() throws IOException {
    	if (usePageAdressMode) {
    		// taken from https://github.com/feng1126/SH1106/blob/master/SH1106.py
    		// and https://arduino.stackexchange.com/questions/13975/porting-sh1106-oled-driver-128-x-64-128-x-32-screen-is-garbled-but-partially
    		for (int page = 0; page < 8; page++) {
    			writeCommand((byte) (0xB0 + page));
    			writeCommand((byte) 0x02);
    			writeCommand((byte) 0x10);
    			for (int i = 0; i < DISPLAY_WIDTH; i++) {
    				device.writeRegister((byte) 0x40, imageBuffer[i+DISPLAY_WIDTH*page]);
    			}
    		}
    	} else {
            writeCommand(SSD1306_COLUMNADDR);
            writeCommand((byte) 0);   // Column start address (0 = reset)
            writeCommand((byte) (DISPLAY_WIDTH - 1)); // Column end address (127 = reset)

            writeCommand(SSD1306_PAGEADDR);
            writeCommand((byte) 0); // Page start address (0 = reset)
            writeCommand((byte) 7); // Page end address

            for (int i = 0; i < ((DISPLAY_WIDTH * DISPLAY_HEIGHT / 8) / 16); i++) {
                // send a bunch of data in one xmission
            	//TODO: see fork here: https://github.com/jackarian/Pi-OLED-V2/blob/master/src/main/java/it/pi4g/pi/oled/OLEDDisplay.java 
                device.write(writeBytes(0x40, imageBuffer, i*16, 16));
            }
    	}
    }
    
    /**
     * This is a convenient method to merge data to sent command to the ssd1306
     * @param localAddress
     * @param buffer
     * @param offset
     * @param size
     * @return
     * @throws IOException
     */
    public byte[] writeBytes(final int localAddress,final byte[] buffer,  final int offset,final int size) throws IOException {
            byte[] buf = new byte[size + 1];
            buf[0] = (byte)localAddress;
            System.arraycopy(buffer, offset, buf, 1, size);
            return buf;
    }
    
    private synchronized void shutdown() {
        try {
            //before we shut down we clear the display
            clear();
            update();
    		
            //now we close the bus
            device.close();
            pi4j.shutdown();
            
        } catch (IOException ex) {
            logger.info("Closing i2c bus");
        }
    }


}
