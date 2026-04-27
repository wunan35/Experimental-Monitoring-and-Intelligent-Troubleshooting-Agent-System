package org.example.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档解析服务
 * 使用 Apache Tika 解析各种格式的文档（PDF, DOC, DOCX, PPT, XLS 等）
 */
@Service
public class DocumentParseService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentParseService.class);

    private final Tika tika = new Tika();
    private final Parser parser = new AutoDetectParser();

    /**
     * 解析文档并提取文本内容
     *
     * @param path 文档路径
     * @return 提取的文本内容
     * @throws IOException 如果读取文件失败
     * @throws TikaException 如果解析失败
     */
    public String parseDocument(Path path) throws IOException, TikaException, SAXException {
        logger.info("使用 Apache Tika 解析文档: {}", path);

        try (InputStream stream = Files.newInputStream(path)) {
            // 创建内容处理器，设置最大文本长度（-1 表示无限制）
            BodyContentHandler handler = new BodyContentHandler(-1);

            // 创建元数据对象
            Metadata metadata = new Metadata();

            // 创建解析上下文
            ParseContext context = new ParseContext();

            // 解析文档
            parser.parse(stream, handler, metadata, context);

            String content = handler.toString();
            logger.info("文档解析完成: {}, 提取文本长度: {} 字符", path, content.length());

            return content;
        }
    }

    /**
     * 检测文档的 MIME 类型
     *
     * @param path 文档路径
     * @return MIME 类型
     * @throws IOException 如果读取文件失败
     */
    public String detectMediaType(Path path) throws IOException {
        String mediaType = tika.detect(path);
        logger.debug("文档类型检测: {} -> {}", path, mediaType);
        return mediaType;
    }
}
