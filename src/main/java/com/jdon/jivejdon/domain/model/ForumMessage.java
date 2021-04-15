/*
 * Copyright 2003-2005 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.jdon.jivejdon.domain.model;

import com.jdon.annotation.Model;
import com.jdon.annotation.model.Inject;
import com.jdon.annotation.model.OnCommand;
import com.jdon.domain.message.DomainMessage;
import com.jdon.jivejdon.domain.command.PostRepliesMessageCommand;
import com.jdon.jivejdon.domain.command.ReviseForumMessageCommand;
import com.jdon.jivejdon.domain.event.MessagePropertiesRevisedEvent;
import com.jdon.jivejdon.domain.event.MessageRevisedEvent;
import com.jdon.jivejdon.domain.event.RepliesMessagePostedEvent;
import com.jdon.jivejdon.domain.event.UploadFilesAttachedEvent;
import com.jdon.jivejdon.domain.model.account.Account;
import com.jdon.jivejdon.domain.model.attachment.AttachmentsVO;
import com.jdon.jivejdon.domain.model.attachment.UploadFile;
import com.jdon.jivejdon.domain.model.message.FilterPipleSpec;
import com.jdon.jivejdon.domain.model.message.MessageUrlVO;
import com.jdon.jivejdon.domain.model.message.MessageVO;
import com.jdon.jivejdon.domain.model.property.MessagePropertysVO;
import com.jdon.jivejdon.domain.model.property.Property;
import com.jdon.jivejdon.domain.model.reblog.ReBlogVO;
import com.jdon.jivejdon.spi.pubsub.publish.MessageEventSourcingRole;
import com.jdon.jivejdon.spi.pubsub.publish.ShortMPublisherRole;
import com.jdon.jivejdon.spi.pubsub.reconstruction.LazyLoaderRole;
import com.jdon.jivejdon.util.Constants;

import java.util.Collection;

/**
 * Aggregate Root
 * <p>
 * This model is Aggregate Root Entity when being RootMessage in ForumThread.
 * ForumThread is another Aggregate Root; in this system, there are two
 * aggregate roots. like Car and Enginee!
 *
 * ForumMessage take care of all in a post such as content, ForumThread take
 * care of all outsied a post.
 * <p>
 *
 * <p>
 *
 * 
 * @author <a href="mailto:banq@163.com">banq</a>
 */
@Model
public class ForumMessage implements Cloneable {
    private static final long serialVersionUID = 1L;
    @Inject
    public LazyLoaderRole lazyLoaderRole;
    @Inject
    public MessageEventSourcingRole eventSourcing;
    @Inject
    public ShortMPublisherRole shortMPublisherRole;

    private Long messageId;
    private MessageVO messageVO;
    private MessageUrlVO messageUrlVO;
    private FilterPipleSpec filterPipleSpec;
    private String creationDate;
    private long modifiedDate;
    private Account account; // owner
    private volatile ForumThread forumThread;
    private Forum forum;

    private String[] tagTitle;
    private AttachmentsVO attachmentsVO;
    private MessagePropertysVO messagePropertysVO;
    private ReBlogVO reBlogVO;
    private volatile boolean solid;

    protected ForumMessage() {
        this.messageVO = this.messageVOBuilder().subject("").body("").build();
        this.messageUrlVO = new MessageUrlVO("", "");
    }

    public static RequireMessageId messageBuilder() {
        return messageId -> parentMessage -> messageVO -> forum -> forumThread -> account -> creationDate -> modifiedDate -> filterPipleSpec -> uploads -> properties -> new FinalStageVO(
                messageId, parentMessage, messageVO, forum, forumThread, account, creationDate, modifiedDate,
                filterPipleSpec, uploads, properties);
    }

    public Account getAccount() {
        return account;
    }

    private void setAccount(Account account) {
        this.account = account;
    }

    public boolean isLeaf() {
        return this.forumThread.isLeaf(this);
    }

    public boolean isRoot() {
        return this.forumThread.isRoot(this);
    }

    public ForumMessage getParentMessage() {
        return isRoot() ? null : ((ForumMessageReply) this).getParentMessage();
    }

    public MessageVO getMessageVO() {
        return messageVO;
    }

    private void setMessageVO(MessageVO messageVO) {
        if (messageVO.getForumMessage() == null || messageVO.getForumMessage() != this) {
            messageVO = this.messageVOBuilder().subject(messageVO.getSubject()).body(messageVO.getBody()).build();
        }
        if (messageVO.getSubject().length() == 0 || messageVO.getBody().length() == 0)
            System.err.println("messageVO is null for messageId=" + this.messageId);
        else if (filterPipleSpec == null) {
            System.err.println("filterPipleSpec is null for messageId=" + this.messageId);
        }

        // apply complex business filter logic to messageVO;
        this.messageVO = filterPipleSpec.apply(messageVO);

    }

    public void updateSubject(String subject) {
        this.messageVO = this.messageVOBuilder().subject(subject).body(this.messageVO.getBody()).build();
    }

    public MessageVO getMessageVOClone() throws Exception {
        return (MessageVO) this.messageVO.clone();
    }

    /**
     * there are two kinds MessageVO; 1. applied business rule filter 2. original
     * that saved in repository
     */
    public void reloadMessageVOOrignal() {
        DomainMessage em = lazyLoaderRole.reloadMessageVO(this.messageId);
        this.messageVO = (MessageVO) em.getBlockEventResult();
        // not with setMessageVO, no filter
        em.clear();
    }

    public boolean isSubjectRepeated(String subject) {
        String lastSubject = getMessageVO().getSubject();
        return lastSubject.equals(subject) ? true : false;
    }

    /**
     * post a reply forumMessage
     */
    @OnCommand("postRepliesMessageCommand")
    public void addChild(PostRepliesMessageCommand postRepliesMessageCommand) {
        try {
            long modifiedDate = System.currentTimeMillis();
            String creationDate = Constants.getDefaultDateTimeDisp(modifiedDate);
            ForumMessageReply forumMessageReply = (ForumMessageReply) ForumMessage.messageBuilder()
                    .messageId(postRepliesMessageCommand.getMessageId()).parentMessage(this)
                    .messageVO(postRepliesMessageCommand.getMessageVO()).forum(this.forum).forumThread(this.forumThread)
                    .acount(postRepliesMessageCommand.getAccount()).creationDate(creationDate)
                    .modifiedDate(modifiedDate).filterPipleSpec(this.filterPipleSpec)
                    .uploads(postRepliesMessageCommand.getAttachment().getUploadFiles())
                    .props(postRepliesMessageCommand.getMessagePropertysVO().getPropertys()).build();

            forumThread.addNewMessage(this, forumMessageReply);
            forumMessageReply.getAccount().updateMessageCount(1);
            eventSourcing.addReplyMessage(new RepliesMessagePostedEvent(postRepliesMessageCommand));
        } catch (Exception e) {
            System.err.print(" addReplyMessage error:" + e + this.messageId);
        }
    }

    @OnCommand("reviseForumMessageCommand")
    public void revise(ReviseForumMessageCommand reviseForumMessageCommand) {
        try {
            setModifiedDate(System.currentTimeMillis());
            MessageVO messageVO = this.messageVOBuilder().subject(reviseForumMessageCommand.getMessageVO().getSubject())
                    .body(reviseForumMessageCommand.getMessageVO().getBody()).build();
            setMessageVO(messageVO);
            forumThread.updateMessage(this);
            Collection<UploadFile> uploads = reviseForumMessageCommand.getAttachment().getUploadFiles();
            if (uploads != null) {
                setAttachment(new AttachmentsVO(this.messageId, uploads));
                eventSourcing.saveUploadFiles(new UploadFilesAttachedEvent(this.messageId, uploads));
            }
            // 3. association message property
            Collection<Property> props = reviseForumMessageCommand.getMessagePropertysVO().getPropertys();
            if (props != null)
                this.getMessagePropertysVO().replacePropertys(props);

            // save this updated message to db
            eventSourcing.saveMessage(new MessageRevisedEvent(reviseForumMessageCommand));

            // merge with old properties;
            eventSourcing.saveMessageProperties(
                    new MessagePropertiesRevisedEvent(this.messageId, getMessagePropertysVO().getPropertys()));
        } catch (Exception e) {
            System.err.print(" updateMessage error:" + e + this.messageId);
        }
    }

    public boolean isMasked() {
        boolean isMasked = this.getMessagePropertysVO().isMasked();
        this.getAccount().setMasked(isMasked);
        return isMasked;
    }

    public void updateMasked(boolean masked) {
        this.getAccount().setMasked(masked);
        this.getMessagePropertysVO().updateMasked(masked);
        eventSourcing.saveMessageProperties(
                new MessagePropertiesRevisedEvent(this.messageId, getMessagePropertysVO().getPropertys()));
        this.getForumThread().updateMessage(this);
        this.reloadMessageVOOrignal();
    }

    public AttachmentsVO getAttachment() {
        return attachmentsVO;
    }

    private void setAttachment(AttachmentsVO attachmentsVO) {
        this.attachmentsVO = attachmentsVO;
    }

    public String getCreationDate() {
        return creationDate;
    }

    private void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public String getCreationDateForDay() {
        return creationDate.substring(2, 16);
    }

    public Long getMessageId() {
        return messageId;
    }

    private void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getModifiedDate() {
        if (this.modifiedDate == 0)
            return "";
        return Constants.getDefaultDateTimeDisp(modifiedDate);
    }

    private void setModifiedDate(long modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getModifiedDate3() {
        if (modifiedDate == 0)
            return "";
        return Constants.convertDataToPretty(modifiedDate);
    }

    public long getModifiedDate2() {
        return modifiedDate;
    }

    public ForumThread getForumThread() {
        return forumThread;
    }

    private void setForumThread(ForumThread forumThread) {
        this.forumThread = forumThread;
    }

    public Forum getForum() {
        return forum;
    }

    private void setForum(Forum forum) {
        if (forum.lazyLoaderRole == null || forum.getName() == null) {
            System.err.println("forum not solid for messageId=" + messageId + " forumId=" + forum.getForumId());
        }
        this.forum = forum;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public int getDigCount() {
        return this.getMessagePropertysVO().getDigCount();
    }

    public MessagePropertysVO getMessagePropertysVO() {
        return messagePropertysVO;
    }

    private void setAttachmentsVO(AttachmentsVO attachmentsVO) {
        this.attachmentsVO = attachmentsVO;
    }

    private void setMessagePropertysVO(MessagePropertysVO messagePropertysVO) {
        this.messagePropertysVO = messagePropertysVO;
    }

    public void messaegDigAction() {
        this.getMessagePropertysVO().addMessageDigCount();
        this.forumThread.addDig(this);
        eventSourcing.saveMessageProperties(
                new MessagePropertiesRevisedEvent(this.messageId, getMessagePropertysVO().getPropertys()));
    }

    public String getPostip() {
        return this.getMessagePropertysVO().getPostip();
    }

    public ReBlogVO getReBlogVO() {
        if (reBlogVO == null)
            reBlogVO = new ReBlogVO(this.forumThread.getThreadId(), this.lazyLoaderRole);
        return reBlogVO;
    }

    public void setReBlogVO(ReBlogVO reBlogVO) {
        this.reBlogVO = reBlogVO;
    }

    public String[] getTagTitle() {
        return tagTitle;
    }

    public void setTagTitle(String[] tagTitle) {
        this.tagTitle = tagTitle;
    }

    private void setFilterPipleSpec(FilterPipleSpec filterPipleSpec) {
        this.filterPipleSpec = filterPipleSpec;
    }

    public MessageUrlVO getMessageUrlVO() {
        return messageUrlVO;
    }

    public void setMessageUrlVO(MessageUrlVO messageUrlVO) {
        this.messageUrlVO = messageUrlVO;
    }

    /**
     * build a messageVO
     */
    public RequireSubject messageVOBuilder() {
        return subject -> body -> new MessageVO.MessageVOFinalStage(subject, body, this);
    }

    @FunctionalInterface
    public interface RequireMessageId {
        RequireParentMessage messageId(long messageId);
    }

    @FunctionalInterface
    public interface RequireParentMessage {
        RequireMessageVO parentMessage(ForumMessage parentMessage);
    }

    @FunctionalInterface
    public interface RequireMessageVO {
        RequireForum messageVO(MessageVO messageVO);
    }

    @FunctionalInterface
    public interface RequireForum {
        RequireForumThread forum(Forum forum);
    }

    @FunctionalInterface
    public interface RequireForumThread {
        RequireAccount forumThread(ForumThread forumThread);
    }

    @FunctionalInterface
    public interface RequireAccount {
        RequireCreationDate acount(Account account);
    }

    @FunctionalInterface
    public interface RequireCreationDate {
        RequireModifiedDate creationDate(String creationDate);
    }

    @FunctionalInterface
    public interface RequireModifiedDate {
        RequireFilterPipleSpec modifiedDate(long modifiedDate);
    }

    @FunctionalInterface
    public interface RequireFilterPipleSpec {
        OptionsUploadFile filterPipleSpec(FilterPipleSpec filterPipleSpec);
    }

    @FunctionalInterface
    public interface OptionsUploadFile {
        OptionsProperties uploads(Collection<UploadFile> uploads);
    }

    @FunctionalInterface
    public interface OptionsProperties {
        FinalStageVO props(Collection<Property> props);
    }

    @FunctionalInterface
    public interface RequireSubject {
        MessageVO.RequireBody subject(String subject);
    }

    @FunctionalInterface
    public interface RequireBody {
        MessageVO.MessageVOFinalStage body(String body);
    }

    public static class FinalStageVO {
        private final long messageId;
        private final ForumMessage parentMessage;
        private final MessageVO messageVO;
        private final Account account;
        private final String creationDate;
        private final long modifiedDate;
        private final Forum forum;
        private final ForumThread forumThread;
        private final FilterPipleSpec filterPipleSpec;
        private final Collection<UploadFile> uploads;
        private final Collection<Property> props;

        public FinalStageVO(long messageId, ForumMessage parentMessage, MessageVO messageVO, Forum forum,
                ForumThread forumThread, Account account, String creationDate, long modifiedDate,
                FilterPipleSpec filterPipleSpec, Collection<UploadFile> uploads, Collection<Property> props) {
            this.messageId = messageId;
            this.parentMessage = parentMessage;
            this.messageVO = messageVO;
            this.account = account;
            this.creationDate = creationDate;
            this.modifiedDate = modifiedDate;
            this.forum = forum;
            this.forumThread = forumThread;
            this.filterPipleSpec = filterPipleSpec;
            this.uploads = uploads;
            this.props = props;
        }

        public ForumMessage build() {
            try {
                if (parentMessage != null) {
                    ForumMessageReply forumMessageRely = new ForumMessageReply();
                    forumMessageRely.build(messageId, messageVO, forum, forumThread, account, creationDate,
                            modifiedDate, filterPipleSpec, uploads, props, parentMessage);
                    return forumMessageRely;
                } else {
                    ForumMessage forumMessage = new ForumMessage();
                    forumMessage.build(messageId, messageVO, forum, forumThread, account, creationDate, modifiedDate,
                            filterPipleSpec, uploads, props);
                    return forumMessage;
                }
            } catch (Exception e) {
                System.err.println("build Exception:" + e.getMessage() + " messageId=" + messageId);
                return null;
            }

        }
    }

    public void build(long messageId, MessageVO messageVO, Forum forum, ForumThread forumThread, Account account,
            String creationDate, long modifiedDate, FilterPipleSpec filterPipleSpec, Collection<UploadFile> uploads,
            Collection<Property> props) {
        try {
            if (!this.isSolid())
                synchronized (this) {
                    if (!this.isSolid()) {
                        setMessageId(messageId);
                        setAccount(account);
                        setCreationDate(creationDate);
                        setModifiedDate(modifiedDate);
                        setForum(forum);
                        setForumThread(forumThread);
                        setFilterPipleSpec(filterPipleSpec);
                        setAttachment(new AttachmentsVO(messageId, uploads));
                        setMessagePropertysVO(new MessagePropertysVO(messageId, props));
                        // apply all filter specification , business rule!
                        messageVO = this.messageVOBuilder().subject(messageVO.getSubject()).body(messageVO.getBody())
                                .build();
                        setMessageVO(messageVO);
                        this.setSolid(true);// construt end
                    }
                }
        } catch (Exception e) {
            System.err.println(" Message build error:" + messageId);
        }

    }

    public boolean isSolid() {
        return solid;
    }

    private void setSolid(boolean solid) {
        this.solid = solid;
    }
}
