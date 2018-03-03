package de.btobastian.javacord.entity.message.impl;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.btobastian.javacord.ImplDiscordApi;
import de.btobastian.javacord.entity.Icon;
import de.btobastian.javacord.entity.Mentionable;
import de.btobastian.javacord.entity.channel.TextChannel;
import de.btobastian.javacord.entity.message.Message;
import de.btobastian.javacord.entity.message.MessageDecoration;
import de.btobastian.javacord.entity.message.MessageFactory;
import de.btobastian.javacord.entity.message.Messageable;
import de.btobastian.javacord.entity.message.embed.EmbedBuilder;
import de.btobastian.javacord.entity.message.embed.impl.ImplEmbedFactory;
import de.btobastian.javacord.entity.user.User;
import de.btobastian.javacord.util.FileContainer;
import de.btobastian.javacord.util.rest.RestEndpoint;
import de.btobastian.javacord.util.rest.RestMethod;
import de.btobastian.javacord.util.rest.RestRequest;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The implementation of {@link MessageFactory}.
 */
public class ImplMessageFactory implements MessageFactory {

    /**
     * The receiver of the message.
     */
    private Messageable messageable = null;

    /**
     * The string builder used to create the message.
     */
    private final StringBuilder strBuilder = new StringBuilder();

    /**
     * The embed of the message. Might be <code>null</code>.
     */
    private EmbedBuilder embed = null;

    /**
     * If the message should be text to speech or not.
     */
    private boolean tts = false;

    /**
     * The nonce of the message.
     */
    private String nonce = null;

    /**
     * A list with all attachments which should be added to the message.
     */
    private final List<FileContainer> attachments = new ArrayList<>();

    /**
     * Creates a new message factory.
     */
    public ImplMessageFactory() { }

    @Override
    public ImplMessageFactory getNewInstance() {
        return new ImplMessageFactory();
    }

    @Override
    public void append(String message, MessageDecoration... decorations) {
        for (MessageDecoration decoration : decorations) {
            strBuilder.append(decoration.getPrefix());
        }
        strBuilder.append(message);
        for (int i = decorations.length - 1; i >= 0; i--) {
            strBuilder.append(decorations[i].getSuffix());
        }
    }

    @Override
    public void appendCode(String language, String code) {
        strBuilder
                .append("\n")
                .append(MessageDecoration.CODE_LONG.getPrefix())
                .append(language)
                .append("\n")
                .append(code)
                .append(MessageDecoration.CODE_LONG.getSuffix());
    }

    @Override
    public void append(Mentionable entity) {
        strBuilder.append(entity.getMentionTag());
    }

    @Override
    public void append(Object object) {
        strBuilder.append(object);
    }

    @Override
    public void appendNewLine() {
        strBuilder.append("\n");
    }

    @Override
    public void setContent(String content) {
        strBuilder.setLength(0);
        strBuilder.append(content);
    }

    @Override
    public void setEmbed(EmbedBuilder embed) {
        this.embed = embed;
    }

    @Override
    public void setTts(boolean tts) {
        this.tts = tts;
    }

    @Override
    public void addFile(BufferedImage image, String fileName) {
        addAttachment(image, fileName);
    }

    @Override
    public void addFile(File file) {
        addAttachment(file);
    }

    @Override
    public void addFile(Icon icon) {
        addAttachment(icon);
    }

    @Override
    public void addFile(URL url) {
        addAttachment(url);
    }

    @Override
    public void addFile(byte[] bytes, String fileName) {
        addAttachment(bytes, fileName);
    }

    @Override
    public void addFile(InputStream stream, String fileName) {
        addAttachment(stream, fileName);
    }

    @Override
    public void addAttachment(BufferedImage image, String fileName) {
        if (image == null || fileName == null) {
            throw new IllegalArgumentException("image and fileName cannot be null!");
        }
        attachments.add(new FileContainer(image, fileName));
    }

    @Override
    public void addAttachment(File file) {
        if (file == null) {
            throw new IllegalArgumentException("file cannot be null!");
        }
        attachments.add(new FileContainer(file));
    }

    @Override
    public void addAttachment(Icon icon) {
        if (icon == null) {
            throw new IllegalArgumentException("icon cannot be null!");
        }
        attachments.add(new FileContainer(icon));
    }

    @Override
    public void addAttachment(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("url cannot be null!");
        }
        attachments.add(new FileContainer(url));
    }

    @Override
    public void addAttachment(byte[] bytes, String fileName) {
        if (bytes == null || fileName == null) {
            throw new IllegalArgumentException("bytes and fileName cannot be null!");
        }
        attachments.add(new FileContainer(bytes, fileName));
    }

    @Override
    public void addAttachment(InputStream stream, String fileName) {
        if (stream == null || fileName == null) {
            throw new IllegalArgumentException("stream and fileName cannot be null!");
        }
        attachments.add(new FileContainer(stream, fileName));
    }

    @Override
    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    @Override
    public StringBuilder getStringBuilder() {
        return strBuilder;
    }

    @Override
    public CompletableFuture<Message> send(User user) {
        return send((Messageable) user);
    }

    @Override
    public CompletableFuture<Message> send(Messageable messageable) {
        if (messageable == null) {
            throw new IllegalStateException("Cannot send message without knowing the receiver");
        }
        if (messageable instanceof TextChannel) {
            return send((TextChannel) messageable);
        } else if (messageable instanceof User) {
            return ((User) messageable).openPrivateChannel().thenCompose(this::send);
        }
        throw new IllegalStateException("Messageable of unknown type");
    }

    @Override
    public CompletableFuture<Message> send(TextChannel channel) {
        ObjectNode body = JsonNodeFactory.instance.objectNode()
                .put("content", toString() == null ? "" : toString() )
                .put("tts", tts);
        body.putArray("mentions");
        if (embed != null) {
            ((ImplEmbedFactory) embed.getFactory()).toJsonNode(body.putObject("embed"));
        }
        if (nonce != null) {
            body.put("nonce", nonce);
        }

        RestRequest<Message> request = new RestRequest<Message>(channel.getApi(), RestMethod.POST, RestEndpoint.MESSAGE)
                .setUrlParameters(channel.getIdAsString());
        if (!attachments.isEmpty() || (embed != null && embed.requiresAttachments())) {
            CompletableFuture<Message> future = new CompletableFuture<>();
            // We access files etc. so this should be async
            channel.getApi().getThreadPool().getExecutorService().submit(() -> {
                try {
                    MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("payload_json", body.toString());
                    List<FileContainer> tempAttachments = new ArrayList<>(attachments);
                    // Add the attachments required for the embed
                    if (embed != null) {
                        tempAttachments.addAll(((ImplEmbedFactory) embed.getFactory()).getRequiredAttachments());
                    }
                    for (int i = 0; i < tempAttachments.size(); i++) {
                        byte[] bytes = tempAttachments.get(i).asByteArray(channel.getApi()).join();

                        String mediaType = URLConnection
                                .guessContentTypeFromName(tempAttachments.get(i).getFileTypeOrName());
                        if (mediaType == null) {
                            mediaType = "application/octet-stream";
                        }
                        multipartBodyBuilder.addFormDataPart("file" + i, tempAttachments.get(i).getFileTypeOrName(),
                                RequestBody.create(MediaType.parse(mediaType), bytes));
                    }

                    request.setMultipartBody(multipartBodyBuilder.build());
                    request.execute(result -> ((ImplDiscordApi) channel.getApi())
                            .getOrCreateMessage(channel, result.getJsonBody()))
                            .whenComplete((message, throwable) -> {
                                if (throwable != null) {
                                    future.completeExceptionally(throwable);
                                } else {
                                    future.complete(message);
                                }
                            });
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        } else {
            request.setBody(body);
            return request.execute(result -> ((ImplDiscordApi) channel.getApi())
                    .getOrCreateMessage(channel, result.getJsonBody()));
        }
    }

    @Override
    public String toString() {
        return strBuilder.toString();
    }

}