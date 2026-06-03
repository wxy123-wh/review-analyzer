package com.wh.reputation.event;

import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.common.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class EventService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;

    public EventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(CreateEventRequest req) {
        if (req == null) {
            throw new BadRequestException("请求体不能为空");
        }
        if (req.productId() == null) {
            throw new BadRequestException("缺少产品编号");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("事件名称不能为空");
        }
        String type = normalizeType(req.type());
        if (type == null) {
            throw new BadRequestException("事件类型只能是活动或版本");
        }
        LocalDate start = parseDate(req.startDate(), "startDate");
        LocalDate end = parseDate(req.endDate(), "endDate");
        if (end.isBefore(start)) {
            throw new BadRequestException("结束日期不能早于开始日期");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                            insert into `event` (product_id, name, type, start_date, end_date, created_at)
                            values (?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, req.productId());
            ps.setString(2, req.name().trim());
            ps.setString(3, type);
            ps.setDate(4, Date.valueOf(start));
            ps.setDate(5, Date.valueOf(end));
            ps.setTimestamp(6, now);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建事件失败");
        }
        return key.longValue();
    }

    public List<EventItemDto> list(Long productId) {
        if (productId == null) {
            throw new BadRequestException("缺少产品编号");
        }
        return jdbcTemplate.query("""
                        select e.id as id,
                               e.name as name,
                               e.type as type,
                               e.start_date as startDate,
                               e.end_date as endDate
                        from `event` e
                        where e.product_id = ?
                        order by e.start_date desc, e.id desc
                        """,
                (rs, rowNum) -> new EventItemDto(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        DATE_FORMAT.format(rs.getDate("startDate").toLocalDate()),
                        DATE_FORMAT.format(rs.getDate("endDate").toLocalDate())
                ),
                productId
        );
    }

    public void delete(Long id) {
        if (id == null) {
            throw new BadRequestException("缺少事件编号");
        }
        int affected = jdbcTemplate.update("delete from `event` where id = ?", id);
        if (affected <= 0) {
            throw new NotFoundException("事件不存在");
        }
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(requiredFieldMessage(field));
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("日期格式不正确：" + value);
        }
    }

    private static String requiredFieldMessage(String field) {
        return switch (field) {
            case "startDate" -> "缺少开始日期";
            case "endDate" -> "缺少结束日期";
            default -> "缺少必要字段";
        };
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String v = type.trim().toLowerCase(Locale.ROOT);
        if (v.equals("activity") || v.equals("version")) {
            return v;
        }
        return null;
    }
}
