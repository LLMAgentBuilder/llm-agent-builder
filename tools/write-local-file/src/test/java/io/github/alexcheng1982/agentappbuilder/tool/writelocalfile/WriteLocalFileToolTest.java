package io.github.alexcheng1982.agentappbuilder.tool.writelocalfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WriteLocalFileToolTest {

  @Test
  void testWriteContent() throws IOException {
    var tool = new WriteLocalFileToolFactory().create();
    String content = "world";
    var request = new WriteLocalFileRequest("hello.txt", null, content);
    var response = tool.apply(request);
    assertNotNull(response.path());
    assertEquals(content, Files.readString(Path.of(response.path())));
  }

  @Test
  void testDownload() {
    var tool = new WriteLocalFileToolFactory().create();
    var request = new WriteLocalFileRequest("hello.txt", "http://www.baidu.com",
        null);
    var response = tool.apply(request);
    assertNotNull(response.path());
  }
}