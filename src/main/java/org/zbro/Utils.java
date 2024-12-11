package org.zbro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static List<String> processCmd(String[] cmd) {
        List<String> strings = new ArrayList<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.redirectErrorStream(true);
            System.out.println("++--++ CMD: " + Arrays.toString(cmd).replace(",", ""));

            Process process = processBuilder.start();
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            StringBuilder line = new StringBuilder();
            int thisByte = 0;
            int lastByte = 0;
            while ((thisByte = reader.read()) != -1) {
                if (thisByte == 10 && lastByte == 13) {
                    strings.add(line.toString());
//                    System.out.print(line);
                    line = new StringBuilder();
                }
                if(lastByte == 13 && thisByte!=10){
                    System.out.print(line);
                    line = new StringBuilder();
                }

                line.append((char) thisByte);

                if (line.toString().contains("[y/N]")) {
                    System.out.println(line);
                    String select = new BufferedReader(new InputStreamReader(System.in)).readLine();
                    process.getOutputStream().write((select + "\n").getBytes());
                    process.getOutputStream().flush();
                    strings.add(line.toString());
                    line = new StringBuilder();
                }
//                if(line.toString().contains("Unable to open")){
//                    System.out.println(line);
//                    String s = line.toString();
//                    System.out.println(s);
//                    System.out.println(s.matches(".*Unable to open.*\\.\\w{3,}"));
//                }
                if (line.toString().matches("\n.*Unable to open.*\\.\\w{3,}")){
                    System.out.println(line);
                    strings.add("fail");
                    return strings;
                }
                if (line.toString().contains("Invalid data found")) {
                    System.out.println(line);
                    strings.add("fail");
                    return strings;
                }

                lastByte = thisByte;
            }
            process.waitFor();
        } catch (Exception exception) {
            if (exception.getMessage().contains("Cannot run program \"ffmpeg\"")) {
                System.out.println("++--++ fail to execute ffmpeg from cmd or bash, install the program with 'apt install ffmpeg' or visit https://ffmpeg.org/download.html");
            }
            exception.printStackTrace();
        }
        return strings;
    }

    public static Map<String,String> detectParas(String input){
        Map<String,String> result = new HashMap<>();

        AtomicReference<String> resolution = new AtomicReference<>();
        AtomicReference<String> videoKbps = new AtomicReference<>();
        AtomicReference<String> audioKbps = new AtomicReference<>();
        AtomicReference<String> totalKbps = new AtomicReference<>();


        String[] getResCommands;

        getResCommands = "ffmpeg -i inputFile".split(" ");
        getResCommands[2] = input;

        System.out.println("++--++ ready to DETECT parameters");

        processCmd(getResCommands).forEach(line->{
            //Duration: 01:03:51.13, start: -0.021000, bitrate: 1467 kb/s
            if (line.contains("Duration")) {
                Pattern pattern = Pattern.compile("bitrate: (\\d{3,}) kb/s");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    totalKbps.set(matcher.group(1));
                    System.out.println("++--++ total kbs:" + totalKbps.get());

                }
            }
            if (line.contains("Video")) {
                Pattern pattern = Pattern.compile("(\\d{3,}x\\d{3,})");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    resolution.set(matcher.group(1));
                }
                pattern = Pattern.compile("(\\d{2,}) kb/s");
                matcher = pattern.matcher(line);
                if (matcher.find()) {
                    videoKbps.set(matcher.group(1));
//                                System.out.println("++--++ videoKbps:" + videoKbps);

                }
            }
            if (line.contains("Audio")) {
                Pattern pattern = Pattern.compile("(\\d{2,}) kb/s");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    audioKbps.set(matcher.group(1));
//                                System.out.println("++--++ audioKbps:" + audioKbps);

                }
            }
        });
        System.out.println("++--++ original resolution:" + resolution.get());
        System.out.println("++--++ original videoKbps:" + videoKbps.get());
        System.out.println("++--++ original audioKbps:" + audioKbps.get());


        if (resolution.get() == null) {
            result.put("resolution","1280x720");
        }else result.put("resolution",resolution.get());

        if (videoKbps.get() == null) {
            result.put("videoKbps","600k");
        } else {
            if (Integer.parseInt(videoKbps.get()) < 600) result.put("videoKbps",videoKbps.get() + "k");
            else result.put("videoKbps","600k");
        }

        if (audioKbps.get() == null) {
            result.put("audioKbps","192k");
        } else {
            if (Integer.parseInt(audioKbps.get()) < 192) result.put("audioKbps",audioKbps.get() + "k");
            else result.put("audioKbps","192k");
        }

        System.out.println("++--++ format resolution:" + result.get("resolution"));
        System.out.println("++--++ format videoKbps:" + result.get("videoKbps"));
        System.out.println("++--++ format audioKbps:" + result.get("audioKbps"));

        return result;
    }

    public static void adjustVolume(String input, String output) {
        AtomicReference<String> originVolume = new AtomicReference<>();

        String[] detectVolunm = "ffmpeg -i fileName -ss 00:02:00 -t 00:08:00 -af volumedetect -f null -".split(" ");
        detectVolunm[2] = input;

        String audioKbps = detectParas(input).get("audioKbps");

        System.out.println("++--++ detect mean volume begin");

        Utils.processCmd(detectVolunm).forEach(line -> {
            if (line.contains("mean_volume:")) {
                Pattern pattern = Pattern.compile("(.* mean_volume: )(-*\\d+.*\\d*)( dB)");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    originVolume.set(matcher.group(2));
                    System.out.println("++--++ mean volume is: " + originVolume);
                }
            }
        });

        if (originVolume.get() == null) {
            System.out.println("++--++ detect origin volume fail, can't adjust volume");
            try {
                if(Files.exists(Paths.get(input))){
                    Files.move(Paths.get(input), Paths.get(output), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return;
        }

        double m = Double.parseDouble(originVolume.get());
        if (Math.max(-17, m) == Math.min(m, -16)) {
            System.out.println("++--++ no need to adjust volume");
            try {
                Files.move(Paths.get(input), Paths.get(output), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } else {
            String adjustPara = -16.5 - Double.parseDouble(originVolume.get()) + "dB";
            String[] adjustVolumeCmd = {"ffmpeg", "-i", input, "-af", "volume=" + adjustPara, "-c:a", "aac", "-b:a", audioKbps, "-c:v", "copy", output};

            System.out.println("++--++ adjust volume begin");

            Utils.processCmd(adjustVolumeCmd);

        }
    }
}
