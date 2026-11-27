package escola.biblioteca;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

final class Inputs {
    private Inputs() {}

    record Name(@NotBlank @Size(max = 160) String name) {}

    record Book(@NotBlank @Size(max = 200) String title,
                @Size(max = 4000) String description,
                @Min(1) @Max(3000) Integer year,
                @NotNull @Positive Long authorId, @NotNull @Positive Long subjectId,
                @Min(0) @Max(100) Integer initialCopies, @NotNull Boolean active) {}

    record Student(@NotBlank @Size(max = 40) String ra,
                   @NotBlank @Size(max = 160) String name,
                   @Email @Size(max = 254) String email,
                   @NotBlank @Size(max = 60) String className,
                   @NotNull Boolean active) {}

    record Quantity(@NotNull @Min(1) @Max(100) Integer quantity) {}

    record Status(@NotNull Boolean active) {}

    record Loan(@NotNull @Positive Long studentId,
                @NotNull @Positive Long bookId,
                @Positive Long copyId,
                @NotNull LocalDate dueDate,
                @Size(max = 1000) String notes) {}

    static String optional(String value) {
        return value == null ? "" : value.strip();
    }
}
