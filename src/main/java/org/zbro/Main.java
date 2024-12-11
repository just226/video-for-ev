package org.zbro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        String README = """
                    NAME
                        auto-video is a simple video process tool to execute wrapped command with ffmpeg. You need to have ffmpeg installed and set the proper path.
                    USAGE
                        java -jar auto-video.jar [d|download|v|volume|p|player|s|subtitles] arg...
                    OPTIONS
                        1.d|download:download videos from url
                        2.v|volume:adjust volume
                        3.p|player:standardize format, burn subtitle, adjust volume. make sure your subtitles are .srt and have the same basename of your video file
                        4.s|subtitles:extract subtitle from video
                        5.i|info:show information of this video
                        6.r|reveal:reveal information from video
                    AUTHOR
                        zbro@flamingo.earth
                        """;
        if (args.length == 0) {
            System.out.println(README);
            return;
        }
        String[] args2 = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "d", "download" -> {
                if (args2.length == 0) {
                    System.out.println("""
                            USAGE
                                java -jar auto-video download https://wwww.youtube.com/some/url/1 https://wwww.youtube.com/some/url/1
                            DESCRIPTION
                                download videos from multiple websites.""");
                    return;
                }
                Arrays.stream(args2).forEach(e -> {
                    String[] downloadCommand = {"yt-dlp", e};
                    Utils.processCmd(downloadCommand);
                    System.out.printf("#%s has been downloaded!%n", e);
                });
            }
            case "v", "volume" -> {
                if (args2.length == 0) {
                    System.out.println("""
                            USAGE
                                java -jar auto-video volume video1 video2 ...
                            DESCRIPTION
                                adjust volume to -16.5dB.""");
                    return;
                }
                Arrays.stream(args2).forEach(e -> {
                    String outputFile = "v-" + e.substring(0,e.lastIndexOf(".")) + ".mp4";
                    Utils.adjustVolume(e,outputFile);
                });
            }


            case "p", "player" -> {
                if (args2.length == 0) {
                    System.out.println("""
                            USAGE
                                java -jar auto-video player video1 video2 ...
                            DESCRIPTION
                                1.format video
                                2.burn subtitles
                                3.adjust volume
                                .""");
                    return;
                }
                Arrays.stream(args2).forEach(e -> {

                    String srtFile = e.substring(0,e.lastIndexOf(".")) + ".srt";
                    String tmp = new Random().nextInt(100) + ".mp4";
                    String outputFile = "a-" + e.substring(0,e.lastIndexOf(".")) + ".mp4";

                    if(Paths.get(outputFile).toFile().exists()){
                        System.out.println( outputFile + " exist, overwrite [y/N]");
                        try {
                            String select = new BufferedReader(new InputStreamReader(System.in)).readLine();
                            if(select.matches("[yY]")){
                                Paths.get(outputFile).toFile().delete();
                            }else if(select.matches("[Nn]")){
                                return;
                            }else return;
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }


                    }

                    Map<String,String> paraMap = Utils.detectParas(e);

                    String[] formatCmd;
                    formatCmd = "ffmpeg -i inputFile -s 1280x720 -r 24 -vf subtitles=S01E01.srt:force_style='FontName=Arial,FontSize=20' -profile:v high -level 3.1 -pix_fmt yuv420p -c:v libx264 -b:v 600k -c:a aac -b:a 192k -ac 2 outputFile"
                            .split(" ");

                    boolean subtitle = false;
                    String[] subTestCmd = "ffmpeg -i sub".split(" ");
                    subTestCmd[2] = srtFile;
                    if(Files.exists(Paths.get(srtFile)) && !Utils.processCmd(subTestCmd).contains("fail")){
                        subtitle = true;
                    }

                    if (subtitle) {
                        formatCmd[2] = e;
                        formatCmd[4] = paraMap.get("resolution");
                        formatCmd[8] = "subtitles=" + srtFile + ":force_style='FontName=Arial,FontSize=20'";
                        formatCmd[18] = paraMap.get("videoKbps");
                        formatCmd[22] = paraMap.get("audioKbps");
                        formatCmd[25] = tmp;
                    } else {
                        System.out.println("++--++ no subtitle found or subtitle is broken, rename or check your subtitle file to:" + srtFile);

                        formatCmd = "ffmpeg -i inputFile -s 1280x720 -r 24 -profile:v high -level 3.1 -pix_fmt yuv420p -c:v libx264 -b:v 600k -c:a aac -b:a 192k -ac 2 outputFile"
                                .split(" ");
                        formatCmd[2] = e;
                        formatCmd[4] = paraMap.get("resolution");
                        formatCmd[16] = paraMap.get("videoKbps");
                        formatCmd[20] = paraMap.get("audioKbps");
                        formatCmd[23] = tmp;
                    }

                    System.out.println("++--++ ready to format video: " + e);


                    if(Utils.processCmd(formatCmd).contains("fail")){
                        if(Files.exists(Paths.get(tmp))){
                            try {
                                Files.delete(Paths.get(tmp));
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                        System.out.println("++--++ format fail!!!!!");
                        return;
                    };

                    Utils.adjustVolume(tmp,outputFile);

                    if(Files.exists(Paths.get(tmp))){
                        try {
                            Files.delete(Paths.get(tmp));
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                });

            }


            case "s", "subtitles" -> {
                if (args2.length == 0) {
                    System.out.println("""
                            USAGE
                                java -jar auto-video subtitles [digit] video1 video2
                            DESCRIPTION
                                extract specific subtitle from videos.
                                [digit] is subtitle stream number, default is 0.
                                
                                this is how to get the stream number.
                                command:
                                    java -jar auto-video info video1
                                output:
                                    Stream #0:0: Video: ...
                                    Stream #0:1(eng): Audio: ...
                                    Metadata:
                                      title           : Stereo
                                    Stream #0:2(ara): Subtitle: ass   ->   0(stream number)
                                    Stream #0:3(chi): Subtitle: ass   ->   1
                                    Stream #0:4(eng): Subtitle: ass   ->   2
                                    """);
                    return;
                }

                String subtitleNumber;
                if (args2[0].matches("^\\d+$")) {
                    subtitleNumber = args2[0];
                    args2 = Arrays.copyOfRange(args2, 1, args2.length);

                } else {
                    subtitleNumber = "0";
                }

                Arrays.stream(args2).forEach(e -> {
                    try {
                        String[] commands = "ffmpeg -i input -map 0:s:0 output".split(" ");
                        commands[4] = "0:s:" + subtitleNumber;
                        commands[2] = e;
                        commands[5] = e.substring(0,e.lastIndexOf(".")) + ".srt";
                        if (Files.exists(Paths.get(commands[5]))) {
                            Files.move(Paths.get(commands[5]), Paths.get("old_subtitle_" + commands[5] + "_" + new Random().nextInt(1000)));
                        }
                        System.out.println("++--++ extract subtitles for " + e);
                        Utils.processCmd(commands);

                        List<String> lines = Files.readAllLines(Paths.get(commands[5])).stream()
                                .map(line -> line.replaceAll("<[.[^>]]*>", "").replaceAll("[{<][.[^}>]]*[}>]", "")).toList();
                        Files.write(Paths.get(commands[5]), lines, StandardCharsets.UTF_8);
                        System.out.println("++--++ extract subtitles success");

                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                });


            }

            case "test" -> {
                if (args2.length == 0) {
                    System.out.println("""
                            #want to do something? try this:
                            #java -jar auto-video test https://wwww.youtube.com/some/url/1 https://wwww.youtube.com/some/url/1""");
                    return;
                }
                Arrays.stream(args2).forEach(e -> {
                    String[] commands = "abc -i input -map 0:s:0 output".split(" ");
                    System.out.println("test begin");
                    Utils.processCmd(commands);
                });
            }
            case "i", "info" -> {
                if (args2.length == 0) {
                    System.out.println("""
                            USAGE
                                java -jar auto-video info video1 video2
                            DESCRIPTION
                                display information of videos.""");
                    return;
                }
                Arrays.stream(args2).forEach(e -> {
                    String[] commands = "ffmpeg -i input".split(" ");
                    commands[2] = e;
                    Utils.processCmd(commands).forEach(System.out::print);
                });
            }

            case "r", "reveal" -> {
                if (args2.length == 0) {
                    System.out.println("""
                            USAGE
                                java -jar auto-video reveal video1 video2
                            DESCRIPTION
                                reveal information of videos.""");
                    return;
                }
                Arrays.stream(args2).forEach(e -> {

                    Utils.detectParas(e);
                });
            }
            case "test-subtitle" -> {
                System.out.println("under construction");
                /*
                1. read subtitles
                2. find the time stamp
                3. sample about 5 minutes with a given subtitle size
                 */
                String command = "ffmpeg -i inputFile -s 1280x720 -r 24 -vf subtitles=S01E01.srt:force_style='FontName=Arial,FontSize=10' -profile:v high -level 3.1 -pix_fmt yuv420p -c:v libx264 -b:v 600k -c:a aac -b:a 192k -ac 2 outputFile";
            }

            default -> System.out.println(README);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("exit");
        }));
    }
}
