package com.example.notify.worker.delivery;

import com.example.notify.worker.config.WorkerProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final WorkerProperties props;

    public SmtpEmailSender(JavaMailSender mailSender, WorkerProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public void send(String to, String subject, String body) {
        String suffix = props.email().simulateFailureSuffix();
        if (suffix != null && !suffix.isBlank() && to != null && to.toLowerCase().endsWith(suffix.toLowerCase())) {
            throw new EmailDeliveryException("Simulated permanent delivery failure for this recipient domain");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.email().from());
            helper.setTo(to);
            helper.setSubject(subject.replaceAll("[\\r\\n]+", " ")); // no header injection
            // Output escaping happens here: user-controlled text is HTML-escaped before it enters the HTML part.
            String html = "<p>" + HtmlUtils.htmlEscape(body).replace("\n", "<br>") + "</p>";
            helper.setText(body, html);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new EmailDeliveryException("SMTP delivery failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
