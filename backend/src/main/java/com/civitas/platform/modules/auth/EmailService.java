package com.civitas.platform.modules.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log=LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;private final boolean consoleFallback;private final String username;
    public EmailService(JavaMailSender mailSender,@Value("${app.otp.log-to-console:true}")boolean consoleFallback,@Value("${spring.mail.username:}")String username,@Value("${civitas.otp.expose-in-development:true}")boolean exposeInDevelopment){this.mailSender=mailSender;this.consoleFallback=consoleFallback&&exposeInDevelopment;this.username=username;}
    public void sendOtp(String address,String code,int expiryMinutes){send(address,"Your CIVITAS verification code","Your CIVITAS verification code is "+code+". It expires in "+expiryMinutes+" minutes. If you did not request this, ignore this email.");}
    public void sendVotingOtp(String address,String code,String electionTitle,int expiryMinutes){send(address,"Your CIVITAS voting verification code","Your CIVITAS code to authenticate for \""+electionTitle+"\" is "+code+". It expires in "+expiryMinutes+" minutes. If you did not request this, ignore this email.");}
    public void sendNotification(String address,String subject,String body){send(address,subject,body);}
    private void send(String address,String subject,String text){if(username==null||username.isBlank()){if(consoleFallback){log.warn("DEV ONLY - CIVITAS email to {}: {} | {}. Configure SMTP before deployment.",address,subject,text);return;}throw new IllegalStateException("SMTP is not configured");}var message=new SimpleMailMessage();message.setFrom(username);message.setTo(address);message.setSubject(subject);message.setText(text);mailSender.send(message);}
    public boolean isSmtpConfigured(){return username!=null&&!username.isBlank();}
}
