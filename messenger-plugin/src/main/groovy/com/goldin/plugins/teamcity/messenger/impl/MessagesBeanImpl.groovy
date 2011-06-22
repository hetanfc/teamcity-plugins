package com.goldin.plugins.teamcity.messenger.impl

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.gcontracts.annotations.Requires
import com.goldin.plugins.teamcity.messenger.api.*
import org.gcontracts.annotations.Invariant


/**
 * {@link MessagesBean} implementation
 */
@Invariant({ this.messagesTable && this.usersTable && this.persistency && this.context && this.util && this.executor })
class MessagesBeanImpl implements MessagesBean
{
    private final MessagesTable       messagesTable
    private final UsersTable          usersTable
    private final MessagesPersistency persistency
    private final MessagesContext     context
    private final MessagesUtil        util
    private final ExecutorService     executor


    @Requires({ messagesTable && usersTable && persistency && context && util })
    MessagesBeanImpl ( MessagesTable messagesTable, UsersTable usersTable, MessagesPersistency persistency,
                       MessagesContext context, MessagesUtil util )
    {
        this.messagesTable = messagesTable
        this.usersTable    = usersTable
        this.persistency   = persistency
        this.context       = context
        this.util          = util
        this.executor      = Executors.newFixedThreadPool( 1 )

        /**
         * Restoring data from persistent storage
         */
        messagesTable.readPersistencyData( persistency.restore())
        usersTable.init( messagesTable.allMessages )
    }


    /**
     * Persists messages table in the background thread
     */
    private void persistMessages ()
    {
        executor.submit({ persistency.persist( messagesTable.persistencyData )} as Runnable )
    }


    @Override
    long sendMessage ( Message message )
    {
        assert ( context.isTest() || context.getUser( message.sender )), "Sender [${ message.sender }] doesn't exist"
        long messageId = usersTable.addMessage( messagesTable.addMessage( message ))

        persistMessages()
        messageId
    }


    @Override
    List<Message> getMessagesForUser ( String username )
    {
        List<Message> messages = []

        messages.addAll( usersTable.getMessagesForUser( username ))
        context.getUserGroups( username ).each { messages.addAll( usersTable.getMessagesForGroup( it )) }
        messages.addAll( usersTable.messagesForAll )

        util.sortForUser( messages.unique  { Message m1, Message m2 -> m1.id <=> m2.id }.
                                   findAll { Message m -> ! m.usersDeleted.contains( username )}.
                                   findAll { Message m -> messagesTable.containsMessage( m.id )},
                          username )
    }


    @Override
    Message deleteMessage ( long messageId )
    {
        def message = messagesTable.deleteMessage( messageId )

        persistMessages()
        message
    }


    @Override
    Message deleteMessageByUser ( long messageId, String username )
    {
        def message = messagesTable.deleteMessageByUser( messageId, username )

        persistMessages()
        message
    }
}
