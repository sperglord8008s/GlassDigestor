// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Generated images/audio and metadata only; no device file or proprietary sample. */
public final class MetadataFixtureFactory {
    // A 16x16 red baseline JPEG generated with FFmpeg's lavfi color and MJPEG encoder.
    private static final String BASE_JPEG =
            "/9j/4AAQSkZJRgABAgAAAQABAAD//gAQTGF2YzYxLjE5LjEwMQD/2wBDAAgGBgcGBwgICAgICAkJCQoKCgkJCQkKCgoKCgoMDAwKCgoKCgoKDAwMDA0ODQ0NDA0ODg8PDxISEREVFRUZGR//xABMAAEBAAAAAAAAAAAAAAAAAAAABgEBAQAAAAAAAAAAAAAAAAAABgcQAQAAAAAAAAAAAAAAAAAAAAARAQAAAAAAAAAAAAAAAAAAAAD/wAARCAAQABADASIAAhEAAxEA/9oADAMBAAIRAxEAPwCLAFF/f//Z";
    public static final int ICC_TAG = 0x64657363;
    public static final String TITLE = "DiE community metadata fixture",
            LARGE = "FOSS-" + repeat('x', 10000);

    private MetadataFixtureFactory() {}
    private static String repeat(char c, int count) { char[] value = new char[count]; Arrays.fill(value,c); return new String(value); }
    public static byte[] jpeg() throws Exception { return jpeg(xmp()); }
    public static byte[] jpeg(String packet) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(new byte[]{(byte)0xff,(byte)0xd8});
        ByteArrayOutputStream exif = new ByteArrayOutputStream(); exif.write(new byte[]{'E','x','i','f',0,0}); exif.write(tiff()); segment(out,0xe1,exif.toByteArray());
        ByteArrayOutputStream xmp = new ByteArrayOutputStream(); xmp.write("http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII)); xmp.write(packet.getBytes(StandardCharsets.UTF_8)); segment(out,0xe1,xmp.toByteArray());
        byte[] profile = profile(); int chunks = (profile.length+59999)/60000;
        for (int i = 0; i < chunks; i++) { ByteArrayOutputStream chunk = new ByteArrayOutputStream(); chunk.write("ICC_PROFILE\0".getBytes(StandardCharsets.US_ASCII)); chunk.write(i+1); chunk.write(chunks); chunk.write(profile,i*60000,Math.min(60000,profile.length-i*60000)); segment(out,0xe2,chunk.toByteArray()); }
        segment(out,0xfe,"first independent comment".getBytes(StandardCharsets.UTF_8)); segment(out,0xfe,"second independent comment".getBytes(StandardCharsets.UTF_8));
        byte[] image = Base64.getDecoder().decode(BASE_JPEG); out.write(image,2,image.length-2); return out.toByteArray();
    }
    private static void segment(OutputStream out, int marker, byte[] value) throws IOException {
        if (value.length > 65533) throw new IOException("Fixture JPEG segment too large");
        int size = value.length+2;
        out.write(0xff); out.write(marker); out.write(size>>>8); out.write(size&255); out.write(value);
    }
    public static String xmp() {
        StringBuilder ancestors = new StringBuilder();
        for (int i = 0; i < 1005; i++)
            ancestors.append("<rdf:li>ancestor-").append(i).append("</rdf:li>");
        return "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF"
                   + " xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description"
                   + " rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
                   + " xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\""
                   + " xmlns:photoshop=\"http://ns.adobe.com/photoshop/1.0/\""
                   + " xmlns:die=\"https://diemobile.invalid/ns/1.0/\" xmp:CreatorTool=\"DiE FOSS"
                   + " fixture\" die:Endpoint=\"https://fixture.invalid/api\" die:Large=\""
                + LARGE
                + "\"><dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">"
                + TITLE
                + "</rdf:li><rdf:li"
                + " xml:lang=\"ja-JP\">研究</rdf:li></rdf:Alt></dc:title><dc:subject><rdf:Bag><rdf:li>FOSS</rdf:li><rdf:li>offline</rdf:li></rdf:Bag></dc:subject><photoshop:DocumentAncestors><rdf:Bag>"
                + ancestors
                + "</rdf:Bag></photoshop:DocumentAncestors></rdf:Description></rdf:RDF></x:xmpmeta>";
    }
    public static byte[] iccValue() {
        byte[] value = new byte[70000]; for (int i = 0; i < value.length; i++) value[i]=(byte)(i&255);
        ByteBuffer buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN); buffer.putInt(0,ICC_TAG); buffer.putInt(4,0); buffer.putInt(8,16);
        byte[] text = "Fixture profile\0".getBytes(StandardCharsets.US_ASCII); System.arraycopy(text,0,value,12,text.length);
        value[512]=(byte)0xff; value[513]=(byte)0xd9; // An embedded marker is metadata, not the image end.
        return value;
    }
    private static byte[] profile() {
        byte[] value = iccValue(); ByteBuffer out = ByteBuffer.allocate(144+value.length).order(ByteOrder.BIG_ENDIAN); out.putInt(0,out.capacity());
        putAscii(out, 4, "DiE ");
        out.putInt(8,0x04300000);
        putAscii(out, 12, "mntr");
        putAscii(out, 16, "RGB ");
        putAscii(out, 20, "XYZ ");
        int[] date = {2026,1,2,3,4,5}; for (int i = 0; i < date.length; i++) out.putShort(24+i*2,(short)date[i]);
        putAscii(out, 36, "acsp");
        putAscii(out, 40, "APPL");
        putAscii(out, 48, "FOSS");
        putAscii(out, 52, "TEST");
        out.putInt(64,1);
        out.putInt(68,63190); out.putInt(72,65536); out.putInt(76,54061);
        putAscii(out, 80, "DiE ");
        out.putInt(128,1); out.putInt(132,ICC_TAG); out.putInt(136,144); out.putInt(140,value.length); out.position(144); out.put(value); return out.array();
    }
    private static void putAscii(ByteBuffer out, int at, String value) { byte[] bytes=value.getBytes(StandardCharsets.US_ASCII); for (int i = 0; i < bytes.length; i++) out.put(at+i,bytes[i]); }
    private static byte[] ascii(String value) {
        return (value + "\0").getBytes(StandardCharsets.US_ASCII);
    }
    private static byte[] rational(int... values) { ByteBuffer out=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN); for (int value:values) out.putInt(value); return out.array(); }
    private static byte[] tiff() throws Exception {
        ByteBuffer out=ByteBuffer.allocate(20000).order(ByteOrder.LITTLE_ENDIAN); out.put(0,(byte)'I'); out.put(1,(byte)'I'); out.putShort(2,(short)42); out.putInt(4,8); int[] cursor={512};
        out.putShort(8,(short)6);
        entry(out, 10, 0x010f, 2, ascii("Fixture Camera"), cursor);
        entry(out, 22, 0x0110, 2, ascii("DiE Community"), cursor);
        entry(out,34,0x0112,3,new byte[]{6,0},cursor);
        entry(out, 46, 0x0132, 2, ascii("2026:01:02 03:04:05"), cursor);
        entry(out,58,0x8769,4,rational(128),cursor); entry(out,70,0x8825,4,rational(256),cursor);
        out.putShort(128,(short)4); entry(out,130,0x829a,5,rational(1,125),cursor); entry(out,142,0x829d,5,rational(28,10),cursor);
        entry(out, 154, 0x9003, 2, ascii("2026:01:02 03:04:05"), cursor);
        entry(
                out,
                166,
                0x9286,
                7,
                ("ASCII\0\0\0Comment " + repeat('c', 9000)).getBytes(StandardCharsets.UTF_8),
                cursor);
        out.putShort(256,(short)4);
        entry(out, 258, 1, 2, ascii("N"), cursor);
        entry(out,270,2,5,rational(37,1,48,1,30,1),cursor);
        entry(out, 282, 3, 2, ascii("W"), cursor);
        entry(out,294,4,5,rational(122,1,24,1,15,1),cursor);
        return Arrays.copyOf(out.array(),cursor[0]);
    }
    private static void entry(ByteBuffer out,int at,int tag,int type,byte[] bytes,int[] cursor) {
        int width=type==3?2:type==4?4:type==5?8:1; out.putShort(at,(short)tag); out.putShort(at+2,(short)type); out.putInt(at+4,bytes.length/width);
        if(bytes.length<=4) for(int i=0;i<bytes.length;i++) out.put(at+8+i,bytes[i]);
        else { out.putInt(at+8,cursor[0]); int previous=out.position(); out.position(cursor[0]); out.put(bytes); out.position(previous); cursor[0]+=(bytes.length+1)&~1; }
    }
    public static byte[] png() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
        ByteBuffer header=ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN); header.putInt(1).putInt(1).put((byte)8).put((byte)6).put((byte)0).put((byte)0).put((byte)0);
        chunk(out, "IHDR", header.array());
        chunk(out, "tEXt", "Author\0FOSS community".getBytes(StandardCharsets.ISO_8859_1));
        chunk(out, "tEXt", "Author\0Second retained value".getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream compressed=new ByteArrayOutputStream(); try(DeflaterOutputStream z=new DeflaterOutputStream(compressed)){z.write(new byte[]{0,(byte)255,0,0,(byte)255});}
        chunk(out, "IDAT", compressed.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }
    private static void chunk(OutputStream target,String type,byte[] value)throws Exception {
        DataOutputStream out=new DataOutputStream(target); byte[] name=type.getBytes(StandardCharsets.US_ASCII); out.writeInt(value.length); out.write(name); out.write(value); CRC32 crc=new CRC32();crc.update(name);crc.update(value);out.writeInt((int)crc.getValue());
    }
    public static byte[] wav() throws Exception {
        ByteArrayOutputStream body=new ByteArrayOutputStream(); body.write("WAVE".getBytes(StandardCharsets.US_ASCII)); ByteBuffer format=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        format.putShort((short)1).putShort((short)2).putInt(44100).putInt(176400).putShort((short)4).putShort((short)16);
        riff(body, "fmt ", format.array());
        ByteArrayOutputStream info=new ByteArrayOutputStream();info.write("INFO".getBytes(StandardCharsets.US_ASCII));
        riff(info, "INAM", ascii("Fixture tone"));
        riff(info, "IART", ascii("FOSS community"));
        riff(body, "LIST", info.toByteArray());
        riff(body, "data", new byte[16]);
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write("RIFF".getBytes(StandardCharsets.US_ASCII));out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size()).array());body.writeTo(out);return out.toByteArray();
    }

    public static byte[] pdf() throws Exception {
        return pdf(0);
    }

    /** A valid large content-stream comment adds bytes without adding document pages. */
    public static byte[] pdf(int padding) throws Exception {
        if (padding < 0 || padding > 16 * 1024 * 1024)
            throw new IOException("PDF fixture padding exceeds 16 MiB");
        String stream = "q Q\n" + (padding == 0 ? "" : "%" + repeat('x', padding) + "\n");
        String[] data = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 144 144] /Contents 4 0 R /Resources << >>"
                    + " >>",
            "<< /Length " + stream.length() + " >>\nstream\n" + stream + "endstream"
        };
        return pdfObjects(data);
    }

    /** Two vector-only pages: red then green, with independently known points and pixels. */
    public static byte[] coloredPdf() throws Exception {
        String red = "q 1 0 0 rg 0 0 144 96 re f Q\n";
        String green = "q 0 1 0 rg 0 0 144 96 re f Q\n";
        return pdfObjects(
                new String[] {
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 144 96] /Contents 4 0 R /Resources"
                        + " << >> >>",
                    "<< /Length " + red.length() + " >>\nstream\n" + red + "endstream",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 144 96] /Contents 6 0 R /Resources"
                        + " << >> >>",
                    "<< /Length " + green.length() + " >>\nstream\n" + green + "endstream"
                });
    }

    private static byte[] pdfObjects(String[] data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write("%PDF-1.4\n%".getBytes(StandardCharsets.US_ASCII));
        bytes.write(new byte[] {(byte) 0xe2, (byte) 0xe3, (byte) 0xcf, (byte) 0xd3, 10});
        List<Integer> objects = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            objects.add(bytes.size());
            bytes.write(
                    ((i + 1) + " 0 obj\n" + data[i] + "\nendobj\n")
                            .getBytes(StandardCharsets.US_ASCII));
        }
        int xref = bytes.size();
        bytes.write(
                ("xref\n0 " + (data.length + 1) + "\n0000000000 65535 f \n")
                        .getBytes(StandardCharsets.US_ASCII));
        for (int at : objects)
            bytes.write(
                    String.format(Locale.ROOT, "%010d 00000 n \n", at)
                            .getBytes(StandardCharsets.US_ASCII));
        bytes.write(
                ("trailer\n<< /Size "
                                + (data.length + 1)
                                + " /Root 1 0 R >>\nstartxref\n"
                                + xref
                                + "\n%%EOF\n")
                        .getBytes(StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }

    private static void riff(OutputStream out, String type, byte[] value) throws Exception {
        out.write(type.getBytes(StandardCharsets.US_ASCII));
        out.write(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.length).array());
        out.write(value);
        if ((value.length & 1) != 0) out.write(0);
    }
}
