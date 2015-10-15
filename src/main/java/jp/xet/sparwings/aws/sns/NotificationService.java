/*
 * Copyright 2015 Miyamoto Daisuke.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.xet.sparwings.aws.sns;

import java.util.HashMap;
import java.util.Map;

import jp.xet.sparwings.aws.ec2.InstanceMetadata;
import jp.xet.sparwings.common.utils.ExceptionUtil;
import jp.xet.sparwings.spring.env.EnvironmentService;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * 開発担当者へのイベント通知サービス。
 * 
 * <p>開発担当者に対して各種イベントやエラー・障害等の通知を行うサービス。</p>
 * 
 * @since 0.3
 * @author daisuke
 */
public class NotificationService implements InitializingBean {
	
	private static Logger logger = LoggerFactory.getLogger(NotificationService.class);
	
	private final String appCodeName;
	
	@Autowired
	AmazonSNS sns;
	
	@Autowired
	InstanceMetadata instanceMetadata;
	
	@Autowired
	EnvironmentService env;
	
	@Value("#{systemProperties['CFN_STACK_NAME']}")
	String stackName;
	
	@Value("#{systemProperties['DEV_TOPIC_ARN']}")
	String devTopicArn;
	
	@Value("#{systemProperties['OPS_TOPIC_ARN']}")
	String opsTopicArn;
	
	
	/**
	 * インスタンスを生成する。
	 * 
	 * @param appCodeName
	 * @since 0.3
	 */
	public NotificationService(String appCodeName) {
		this.appCodeName = appCodeName;
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {
		logger.info("devTopicArn = {}", devTopicArn);
		logger.info("opsTopicArn = {}", opsTopicArn);
	}
	
	/**
	 * 運用担当者にメッセージを通知する。
	 * 
	 * @param subject タイトル
	 * @param message メッセージ本文
	 * @since 0.3
	 */
	public void notifyOps(String subject, String message) {
		notifyMessage0(opsTopicArn, subject, message);
	}
	
	/**
	 * 開発担当者にメッセージを通知する。
	 * 
	 * @param subject タイトル
	 * @param message メッセージ本文
	 * @since 0.3
	 */
	public void notifyDev(String subject, String message) {
		notifyDev(subject, message, null);
	}
	
	/**
	 * 開発担当者に例外エラーメッセージを通知する。
	 * 
	 * @param subject タイトル
	 * @param message メッセージ本文
	 * @param t 例外
	 * @since 0.3
	 */
	public void notifyDev(String subject, String message, Throwable t) {
		Map<String, String> messageMap = new HashMap<>();
		messageMap.put("message", message);
		notifyDev(subject, messageMap, t);
	}
	
	/**
	 * 開発担当者に例外エラーメッセージを通知する。
	 * 
	 * @param t 例外
	 * @since 0.3
	 */
	public void notifyDev(Throwable t) {
		notifyDev("unexpected exception", new HashMap<>(), t);
	}
	
	/**
	 * 開発担当者に例外エラーメッセージを通知する。
	 * 
	 * @param message メッセージ本文
	 * @param t 例外
	 * @since 0.3
	 */
	public void notifyDev(String message, Throwable t) {
		Map<String, String> messageMap = new HashMap<>();
		messageMap.put("message", message);
		notifyDev("unexpected exception", messageMap, t);
	}
	
	/**
	 * 開発担当者にメッセージを通知する。
	 *
	 * @param subject タイトル
	 * @param messageMap メッセージ
	 * @param t 例外
	 * @since 0.3
	 */
	public void notifyDev(String subject, Map<String, String> messageMap, Throwable t) {
		messageMap.put("profiles", env.getActiveProfilesAsString());
		messageMap.put("instance id", instanceMetadata.getInstanceId());
		messageMap.put("instance detail", instanceMetadata.toString());
		if (t != null) {
			messageMap.put("stackTrace", ExceptionUtil.toString(t));
		}
		
		notifyMessage0(devTopicArn, subject, createMessage(messageMap));
	}
	
	private String createMessage(Map<String, String> messageMap) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : messageMap.entrySet()) {
			sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
		}
		return sb.toString();
	}
	
	private void notifyMessage0(String topicArn, String subject, String message) {
		subject = String.format("[%s:%s] %s (%s)", appCodeName, stackName, subject, env.getActiveProfilesAsString());
		if (subject.length() > 100) {
			logger.warn("Topic message subject is truncated.  Full subject is: {}", subject);
			subject = subject.substring(0, 100);
		}
		
		logger.debug("notify message to topic[{}] - {} : {}", topicArn, subject, message);
		if (Strings.isNullOrEmpty(topicArn) || topicArn.equals("arn:aws:sns:null")) {
			logger.debug("topicArn: NULL");
			return;
		}
		try {
			sns.publish(new PublishRequest()
				.withTopicArn(topicArn)
				.withSubject(subject)
				.withMessage(message));
		} catch (Exception e) {
			logger.error("SNS Publish failed: {} - {} - {}", topicArn, subject, message, e);
		}
	}
}
