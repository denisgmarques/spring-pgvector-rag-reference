$(function () {
    var STATUS_BADGE_CLASS = {
        PENDING: 'text-bg-secondary',
        PROCESSING: 'text-bg-info',
        PROCESSED: 'text-bg-success',
        ERROR: 'text-bg-danger'
    };

    var nextCursor = null;
    var loading = false;
    var exhausted = false;

    function renderRow(doc) {
        var $row = $($('#document-row-template').prop('content')).find('tr').clone();
        $row.attr('data-id', doc.id);
        $row.find('.doc-file-name').text(doc.fileName);
        $row.find('.doc-status-badge')
            .addClass(STATUS_BADGE_CLASS[doc.status] || 'text-bg-secondary')
            .text(doc.status);
        $row.find('.doc-uploaded-at').text(doc.uploadedAt);
        $row.find('.doc-edit-btn').on('click', function () {
            $row.find('.doc-edit-input').trigger('click');
        });
        $row.find('.doc-edit-input').on('change', function () {
            var files = this.files;
            if (!files.length) {
                return;
            }
            var formData = new FormData();
            formData.append('file', files[0]);
            $.ajax({
                url: '/api/documents/' + doc.id,
                type: 'PUT',
                data: formData,
                processData: false,
                contentType: false
            }).done(function () {
                reload();
            }).fail(function (xhr) {
                showFeedback(errorMessage(xhr));
            });
        });
        $row.find('.doc-delete-btn').on('click', function () {
            $.ajax({
                url: '/api/documents/' + doc.id,
                type: 'DELETE'
            }).done(function () {
                $row.remove();
            }).fail(function (xhr) {
                showFeedback(errorMessage(xhr));
            });
        });
        return $row;
    }

    function errorMessage(xhr) {
        return (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : 'Falha ao processar a solicitação.';
    }

    function showFeedback(message) {
        $('#documents-feedback').removeClass('d-none').addClass('alert-danger').text(message);
    }

    function loadNextBatch() {
        if (loading || exhausted) {
            return;
        }
        loading = true;
        $('#documents-loading').removeClass('d-none');

        $.ajax({
            url: '/api/documents',
            type: 'GET',
            data: nextCursor ? {cursor: nextCursor} : {}
        }).done(function (page) {
            page.items.forEach(function (doc) {
                $('#documents-tbody').append(renderRow(doc));
            });
            nextCursor = page.nextCursor;
            if (!nextCursor) {
                exhausted = true;
            }
            $('#documents-empty').toggleClass('d-none', $('#documents-tbody').children().length > 0);
        }).always(function () {
            loading = false;
            $('#documents-loading').addClass('d-none');
        });
    }

    function reload() {
        $('#documents-tbody').empty();
        nextCursor = null;
        exhausted = false;
        loadNextBatch();
    }

    $(window).on('scroll', function () {
        var nearBottom = $(window).scrollTop() + $(window).height() >= $(document).height() - 200;
        if (nearBottom) {
            loadNextBatch();
        }
    });

    loadNextBatch();
});
