package escola.biblioteca;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
class CatalogController {
    private final Library library;

    CatalogController(Library library) {
        this.library = library;
    }

    @GetMapping
    Map<String, Object> catalog(@RequestParam(defaultValue = "") String q,
                                @RequestParam(defaultValue = "0") long subject,
                                @RequestParam(defaultValue = "1") int page) {
        if (q.length() > 120 || page < 1 || page > 100_000 || subject < 0) {
            throw Library.bad("Parâmetros de busca inválidos.");
        }
        return library.catalog(q, subject, page);
    }

    @GetMapping("/subjects")
    List<Map<String, Object>> subjects() {
        return library.publicSubjects();
    }

    @GetMapping("/{id}")
    Map<String, Object> book(@PathVariable long id) {
        return library.publicBook(id);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> requestLoan(@Valid @RequestBody Inputs.PublicRequest input) {
        return library.requestLoan(input);
    }

    @GetMapping("/requests/{trackingCode}")
    Map<String, Object> track(@PathVariable UUID trackingCode) {
        return library.track(trackingCode);
    }

    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    byte[] qr(@RequestParam String url) throws WriterException, IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException error) {
            throw Library.bad("Informe o endereço completo da página inicial do catálogo.");
        }
        boolean localhost = "http".equalsIgnoreCase(uri.getScheme()) && ("localhost".equals(uri.getHost())
                || "127.0.0.1".equals(uri.getHost()));
        if (url.length() > 512 || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("".equals(uri.getPath()) || "/".equals(uri.getPath()))
                || !(localhost || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw Library.bad("Use a página inicial do catálogo, com HTTPS para um endereço público.");
        }
        var matrix = new QRCodeWriter().encode(uri.toASCIIString(), BarcodeFormat.QR_CODE, 480, 480);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", png);
        return png.toByteArray();
    }
}
