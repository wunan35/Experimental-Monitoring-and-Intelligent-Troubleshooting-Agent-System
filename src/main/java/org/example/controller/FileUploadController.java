package org.example.controller;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.config.FileUploadConfig;
import org.example.dto.ApiResponse;
import org.example.dto.FileUploadRes;
import org.example.exception.ValidationException;
import org.example.service.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传控制器
 * 支持限流保护、参数校验
 *
 * @author OnCall Agent Team
 * @version 1.0.0
 */
@RestController
@Tag(name = "upload", description = "文件上传 - 知识库文档管理")
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private FileUploadConfig fileUploadConfig;

    @Autowired
    private VectorIndexService vectorIndexService;

    @Autowired
    @Qualifier("uploadRateLimiter")
    private RateLimiter uploadRateLimiter;

    @Operation(
            summary = "上传文件到知识库",
            description = "上传文档文件（如Markdown、TXT、PDF等），系统将自动解析并构建向量索引。" +
                    "支持的文件格式：md, txt, pdf, doc, docx。最大文件大小：10MB。"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "上传成功",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FileUploadRes.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "请求被限流"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "参数校验失败（文件为空、格式不支持、大小超限）"
            )
    })
    @PostMapping(value = "/api/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @Parameter(description = "上传的文件", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            // 使用限流器保护接口
            return uploadRateLimiter.executeSupplier(() -> doUpload(file));
        } catch (RequestNotPermitted e) {
            logger.warn("文件上传接口被限流");
            return ResponseEntity.status(429)
                    .body(ApiResponse.error("请求过于频繁，请稍后重试"));
        }
    }

    /**
     * 执行文件上传逻辑
     */
    private ResponseEntity<?> doUpload(MultipartFile file) {
        // 参数校验
        if (file.isEmpty()) {
            throw new ValidationException("FILE_EMPTY", "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new ValidationException("FILENAME_EMPTY", "文件名不能为空");
        }

        // 文件大小校验（限制10MB）
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new ValidationException("FILE_TOO_LARGE",
                    String.format("文件大小超过限制，最大允许 %d MB", maxSize / 1024 / 1024));
        }

        String fileExtension = getFileExtension(originalFilename);
        if (!isAllowedExtension(fileExtension)) {
            throw new ValidationException("FILE_TYPE_NOT_ALLOWED",
                    "不支持的文件格式，仅支持: " + fileUploadConfig.getAllowedExtensions());
        }

        try {
            String uploadPath = fileUploadConfig.getPath();
            Path uploadDir = Paths.get(uploadPath).normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 使用原始文件名，实现基于文件名的去重
            Path filePath = uploadDir.resolve(originalFilename).normalize();

            // 如果文件已存在，先删除旧文件（实现覆盖更新）
            if (Files.exists(filePath)) {
                logger.info("文件已存在，将覆盖: {}", filePath);
                Files.delete(filePath);
            }

            Files.copy(file.getInputStream(), filePath);
            logger.info("文件上传成功: {}", filePath);

            // 文件上传成功后，自动调用向量索引服务
            try {
                logger.info("开始为上传文件创建向量索引: {}", filePath);
                vectorIndexService.indexSingleFile(filePath.toString());
                logger.info("向量索引创建成功: {}", filePath);
            } catch (Exception e) {
                logger.error("向量索引创建失败: {}, 错误: {}", filePath, e.getMessage(), e);
                // 注意：即使索引失败，文件上传仍然成功，只是记录错误日志
            }

            FileUploadRes response = new FileUploadRes(
                    originalFilename,
                    filePath.toString(),
                    file.getSize()
            );

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (IOException e) {
            logger.error("文件上传失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("文件上传失败: " + e.getMessage()));
        }
    }

    private String getFileExtension(String filename) {
        int lastIndexOf = filename.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return filename.substring(lastIndexOf + 1).toLowerCase();
    }

    private boolean isAllowedExtension(String extension) {
        String allowedExtensions = fileUploadConfig.getAllowedExtensions();
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return false;
        }
        List<String> allowedList = Arrays.asList(allowedExtensions.split(","));
        return allowedList.contains(extension.toLowerCase());
    }
}
