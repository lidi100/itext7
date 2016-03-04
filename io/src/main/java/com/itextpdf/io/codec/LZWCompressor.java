package com.itextpdf.io.codec;


import java.io.IOException;
import java.io.OutputStream;

/**
 * Modified from original LZWCompressor to change interface to passing a
 * buffer of data to be compressed.
 */
public class LZWCompressor {
    /**
     * base underlying code size of data being compressed 8 for TIFF, 1 to 8 for GIF
     **/
    int codeSize_;

    /**
     * reserved clear code based on code size
     **/
    int clearCode_;

    /**
     * reserved end of data code based on code size
     **/
    int endOfInfo_;

    /**
     * current number bits output for each code
     **/
    int numBits_;

    /**
     * limit at which current number of bits code size has to be increased
     **/
    int limit_;

    /**
     * the prefix code which represents the predecessor string to current input point
     **/
    short prefix_;

    /**
     * output destination for bit codes
     **/
    BitFile bf_;

    /**
     * general purpose LZW string table
     **/
    LZWStringTable lzss_;

    /**
     * modify the limits of the code values in LZW encoding due to TIFF bug / feature
     **/
    boolean tiffFudge_;

    /**
     * @param out      destination for compressed data
     * @param codeSize the initial code size for the LZW compressor
     * @param TIFF     flag indicating that TIFF lzw fudge needs to be applied
     * @throws IOException if underlying output stream error
     **/
    public LZWCompressor(OutputStream out, int codeSize, boolean TIFF) throws IOException {
        bf_ = new BitFile(out, !TIFF);    // set flag for GIF as NOT tiff
        codeSize_ = codeSize;
        tiffFudge_ = TIFF;
        clearCode_ = 1 << codeSize_;
        endOfInfo_ = clearCode_ + 1;
        numBits_ = codeSize_ + 1;

        limit_ = (1 << numBits_) - 1;
        if (tiffFudge_)
            --limit_;

        prefix_ = (short) 0xFFFF;
        lzss_ = new LZWStringTable();
        lzss_.ClearTable(codeSize_);
        bf_.writeBits(clearCode_, numBits_);
    }

    /**
     * @param buf data to be compressed to output stream
     * @throws IOException if underlying output stream error
     **/
    public void compress(byte[] buf, int offset, int length)
            throws IOException {
        int idx;
        byte c;
        short index;

        int maxOffset = offset + length;
        for (idx = offset; idx < maxOffset; ++idx) {
            c = buf[idx];
            if ((index = lzss_.FindCharString(prefix_, c)) != -1)
                prefix_ = index;
            else {
                bf_.writeBits(prefix_, numBits_);
                if (lzss_.AddCharString(prefix_, c) > limit_) {
                    if (numBits_ == 12) {
                        bf_.writeBits(clearCode_, numBits_);
                        lzss_.ClearTable(codeSize_);
                        numBits_ = codeSize_ + 1;
                    } else
                        ++numBits_;

                    limit_ = (1 << numBits_) - 1;
                    if (tiffFudge_)
                        --limit_;
                }
                prefix_ = (short) ((short) c & 0xFF);
            }
        }
    }

    /**
     * Indicate to compressor that no more data to go so write out
     * any remaining buffered data.
     *
     * @throws IOException if underlying output stream error
     **/
    public void flush() throws IOException {
        if (prefix_ != -1)
            bf_.writeBits(prefix_, numBits_);

        bf_.writeBits(endOfInfo_, numBits_);
        bf_.flush();
    }
}