package web;


import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Servlet implementation class UploadServlet
 */
@WebServlet("/UploadServlet")
public class UploadServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // 上传文件存储目录
    private static final String UPLOAD_DIRECTORY = "upload";

    // 上传配置
    private static final int MEMORY_THRESHOLD = 1024 * 1024 * 3;  // 3MB
    private static final int MAX_FILE_SIZE = 1024 * 1024 * 40; // 40MB
    private static final int MAX_REQUEST_SIZE = 1024 * 1024 * 50; // 50MB

    //调试
    private ServletContext context;

    //图片队列
    private Queue<File> imgQueue = new ConcurrentLinkedDeque<>();
    private String imgName = "0";

    private int imgLength = 800;

    @Override
    public void init() throws ServletException {
        context = getServletContext();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        context.log("do get");

        resp.setContentType("text/plain");

        PrintWriter writer = resp.getWriter();

        String path = req.getServletContext().getRealPath("./") + File.separator + UPLOAD_DIRECTORY + File.separator + imgName;
        context.log(String.valueOf(new File(path).exists()));

        if (req.getQueryString() == null || req.getQueryString().isEmpty()) {
            if (!imgQueue.isEmpty()) {
                try {
                    new File(path).delete();
                } catch (Exception e) {
                }
                imgName = imgQueue.poll().getName();
            }

            writer.print(imgName);
        } else {
            writer.print(imgQueue.size() - 1);
        }
        writer.close();
    }

    /**
     * 上传数据及保存文件
     */
    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws ServletException, IOException {

        context.log("do post");

        // 检测是否为多媒体上传
        if (!ServletFileUpload.isMultipartContent(request)) {
            // 如果不是则停止
            PrintWriter writer = response.getWriter();
            writer.print("Error: 表单必须包含 enctype=multipart/form-data");
            writer.flush();
            return;
        }

        // 配置上传参数
        DiskFileItemFactory factory = new DiskFileItemFactory();
        // 设置内存临界值 - 超过后将产生临时文件并存储于临时目录中
        factory.setSizeThreshold(MEMORY_THRESHOLD);
        // 设置临时存储目录
        factory.setRepository(new File(System.getProperty("java.io.tmpdir")));

        ServletFileUpload upload = new ServletFileUpload(factory);

        // 设置最大文件上传值
        upload.setFileSizeMax(MAX_FILE_SIZE);

        // 设置最大请求值 (包含文件和表单数据)
        upload.setSizeMax(MAX_REQUEST_SIZE);

        // 中文处理
//        upload.setHeaderEncoding("UTF-8");

        // 构造临时路径来存储上传的文件
        // 这个路径相对当前应用的目录
        String uploadPath = request.getServletContext().getRealPath("./") + File.separator + UPLOAD_DIRECTORY;


        // 如果目录不存在则创建
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdir();
        }

        try {
            // 解析请求的内容提取文件数据
            @SuppressWarnings("unchecked")
            List<FileItem> formItems = upload.parseRequest(request);

            if (formItems != null && formItems.size() > 0) {
                // 迭代表单数据
                for (FileItem item : formItems) {
                    // 处理不在表单中的字段
                    if (!item.isFormField()) {
                        String fileName = UUID.randomUUID().toString() + ".jpg";
                        String filePath = uploadPath + File.separator + fileName;
                        File storeFile = new File(filePath);

                        // 在控制台输出文件的上传路径
                        context.log(filePath);
                        // 保存文件到硬盘
                        item.write(storeFile);

                        //压缩至指定图片尺寸（例如：横90高900），保持图片不变形，多余部分裁剪掉
                        BufferedImage image = ImageIO.read(storeFile);
                        Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(image);

                        int imageWidth = image.getWidth();
                        int imageHeight = image.getHeight();
                        if ((float) imgLength / imgLength != (float) imageWidth / imageHeight) {
                            if (imageWidth > imageHeight) {
                                builder.height(imgLength);
                            } else {
                                builder.width(imgLength);
                            }
                            builder = Thumbnails.of(builder.asBufferedImage())
                                    .rotate(rotateIOSImg(storeFile))
                                    .sourceRegion(Positions.CENTER, imgLength, imgLength)
                                    .size(imgLength, imgLength);
                        } else {
                            builder.size(imgLength, imgLength);
                        }
                        builder.toFile(storeFile);

//                        //压缩和缩放图片
//                        context.log("image length: " + storeFile.length());
//
//                        Thumbnails.Builder builder = Thumbnails.of(storeFile);
//
//                        while (storeFile.length() > 307200) {
//                            context.log("scale image: " + storeFile.length());
//                            builder.scale(0.8);
//                        }
//
//                        builder.scale(1).toFile(storeFile);
//
//                        BufferedImage image = ImageIO.read(storeFile);
//                        int size = Math.min(image.getHeight(), image.getWidth());
//                        builder.sourceRegion(Positions.CENTER, size, size).toFile(storeFile);

                        imgQueue.add(storeFile);

                        request.setAttribute("message", "文件上传成功!");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute("message", "错误信息: " + e.getMessage());
        }

        // 设置响应内容类型
        response.setContentType("text/html;charset=UTF-8");

        // 要重定向的新位置
        String site = new String("/webDemo/img/success.html");

        response.setStatus(response.SC_MOVED_TEMPORARILY);
        response.setHeader("Location", site);
//        request.getServletContext().getRequestDispatcher("/index.html").forward(request, response);
    }

    private int rotateIOSImg(File file) {

        Metadata metadata;
        int angel = 0;

        try {
            metadata = ImageMetadataReader.readMetadata(file);
//            Directory directory = metadata.getDirectory(ExifDirectory.class);
            Collection<ExifSubIFDDirectory> directory = metadata.getDirectoriesOfType(ExifSubIFDDirectory.class);

            for (ExifSubIFDDirectory exif : directory) {
                if (exif.containsTag(ExifSubIFDDirectory.TAG_ORIENTATION)) {
                    // Exif信息中方向　　
                    int orientation = exif.getInt(ExifSubIFDDirectory.TAG_ORIENTATION);
                    // 原图片的方向信息
                    if (6 == orientation) {
                        //6逆时针旋转90
                        angel = 90;
                    } else if (3 == orientation) {
                        //3旋转180
                        angel = 180;
                    } else if (8 == orientation) {
                        //8顺时针旋转90
                        angel = 270;
                    }
                    return angel;
                }
            }
        } catch (JpegProcessingException e) {
            e.printStackTrace();
        } catch (MetadataException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ImageProcessingException e) {
            e.printStackTrace();
        }

        context.log("图片旋转类型角度：" + angel);

        return angel;
    }
}
